package org.fbi.fskfq.processor;


import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.fbi.fskfq.domain.cbs.T4010Request.CbsTia4010;
import org.fbi.fskfq.domain.cbs.T4010Response.CbsToa4010;
import org.fbi.fskfq.domain.cbs.T4010Response.CbsToa4010Item;
import org.fbi.fskfq.domain.tps.base.TpsTia;
import org.fbi.fskfq.domain.tps.base.TpsToaXmlBean;
import org.fbi.fskfq.domain.tps.txn.TpsTia2401;
import org.fbi.fskfq.domain.tps.txn.TpsToa9000;
import org.fbi.fskfq.domain.tps.txn.TpsToa9910;
import org.fbi.fskfq.enums.BillStatus;
import org.fbi.fskfq.enums.TxnRtnCode;
import org.fbi.fskfq.helper.FbiBeanUtils;
import org.fbi.fskfq.helper.MybatisFactory;
import org.fbi.fskfq.repository.dao.FsKfqPaymentInfoMapper;
import org.fbi.fskfq.repository.dao.FsKfqPaymentItemMapper;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfo;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfoExample;
import org.fbi.fskfq.repository.model.FsKfqPaymentItem;
import org.fbi.fskfq.repository.model.FsKfqPaymentItemExample;
import org.fbi.linking.codec.dataformat.SeperatedTextDataFormat;
import org.fbi.linking.processor.ProcessorException;
import org.fbi.linking.processor.standprotocol10.Stdp10ProcessorRequest;
import org.fbi.linking.processor.standprotocol10.Stdp10ProcessorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 1534010�ɿ��ѯ
 * zhanrui
 * 20131227
 */
public class T4010Processor extends AbstractTxnProcessor {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void doRequest(Stdp10ProcessorRequest request, Stdp10ProcessorResponse response) throws ProcessorException, IOException {
        CbsTia4010 tia;
        try {
            tia = getCbsTia(request.getRequestBody());
            logger.info("��ɫҵ��ƽ̨������TIA:" + tia.toString());
        } catch (Exception e) {
            logger.error("��ɫҵ��ƽ̨�����Ľ�������.", e);
            response.setHeader("rtnCode", TxnRtnCode.CBSMSG_UNMARSHAL_FAILED.getCode());
            return;
        }

        //��鱾�����ݿ���Ϣ
        FsKfqPaymentInfo paymentInfo_db = selectNotCanceledPaymentInfoFromDB(tia.getBillNo());
        if (paymentInfo_db != null) {
            String billStatus = paymentInfo_db.getLnkBillStatus();
            switch (billStatus) {
                case "0":  //δ�ɿ�������Ѵ�����Ϣ
                    List<FsKfqPaymentItem> paymentItems = selectPaymentItemsFromDB(paymentInfo_db);
                    String starringRespMsg = getRespMsgForStarring(paymentInfo_db, paymentItems);
                    response.setHeader("rtnCode", TxnRtnCode.TXN_EXECUTE_SECCESS.getCode());
                    response.setResponseBody(starringRespMsg.getBytes(response.getCharacterEncoding()));
                    return;
                case "1":  //�ѽɿ�
                    response.setHeader("rtnCode", TxnRtnCode.TXN_PAY_REPEATED.getCode());
                    logger.info("===�˱ʽɿ�ѽɿ�.");
                    return;
                default:
                    throw new RuntimeException("�ɿ״̬����.");
            }
        }

        //������ͨѶ����
        TpsTia tpsTia = assembleTpsRequestBean(tia, request);
        TpsToaXmlBean tpsToa = null;

        byte[] sendTpsBuf;
        try {
            sendTpsBuf = generateTpsTxMsgHeader(tpsTia, request);
        } catch (Exception e) {
            logger.error("���ɵ�����������������ʱ����.", e);
            response.setHeader("rtnCode", TxnRtnCode.TPSMSG_MARSHAL_FAILED.getCode());
            return;
        }

        try {
            String dataType = tpsTia.getHeader().getDataType();
            byte[] recvTpsBuf = processThirdPartyServer(sendTpsBuf, dataType);
            String recvTpsMsg = new String(recvTpsBuf, "GBK");

            String rtnDataType = substr(recvTpsMsg, "<dataType>", "</dataType>").trim();
            if ("9910".equals(rtnDataType)) { //�������쳣���� 9910
                TpsToa9910 tpsToa9910 = transXmlToBeanForTps9910(recvTpsBuf);
                //TODO ����ǩ������
                T9905Processor t9905Processor = new T9905Processor();
                t9905Processor.doRequest(request, response);
                assembleAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, "��Ȩ��䶯�������·����ס�", response);
                //assembleAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, tpsToa9910.Body.Object.Record.add_word, response);
                logger.info("===���������������ر���(�쳣ҵ����Ϣ��)��\n" + tpsToa9910.toString());
                return;
            } else { //ҵ�����������쳣���� 2401
                tpsToa = transXmlToBeanForTps(recvTpsBuf);
            }
        } catch (SocketTimeoutException e) {
            logger.error("�������������ͨѶ������ʱ.", e);
            response.setHeader("rtnCode", TxnRtnCode.MSG_RECV_TIMEOUT.getCode());
            return;
        } catch (Exception e) {
            logger.error("�������������ͨѶ�����쳣.", e);
            response.setHeader("rtnCode", TxnRtnCode.MSG_COMM_ERROR.getCode());
            return;
        }

        //��������������ҵ����--
        String starringRespMsg = "";

        String result = tpsToa.getMaininfoMap().get("RESULT");
        if (result != null) { //�쳣ҵ����
            TpsToa9000 tpsToa9000 = new TpsToa9000();
            try {
                FbiBeanUtils.copyProperties(tpsToa.getMaininfoMap(), tpsToa9000);
                assembleAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, tpsToa9000.getAddWord(), response);
                return;
            } catch (Exception e) {
                logger.error("��������������Ӧ���Ľ����쳣.", e);
                response.setHeader("rtnCode", TxnRtnCode.TXN_EXECUTE_FAILED.getCode());
                return;
            }
        }

        FsKfqPaymentInfo paymentInfo = new FsKfqPaymentInfo();
        List<FsKfqPaymentItem> paymentItems = new ArrayList<>();
        try {
            FbiBeanUtils.copyProperties(tpsToa.getMaininfoMap(), paymentInfo, true);
            List<Map<String, String>> detailMaplist = tpsToa.getDetailMapList();
            for (Map<String, String> detailMap : detailMaplist) {
                FsKfqPaymentItem item = new FsKfqPaymentItem();
                FbiBeanUtils.copyProperties(detailMap, item, true);
                paymentItems.add(item);
            }
        } catch (Exception e) {
            logger.error("��������������Ӧ���Ľ����쳣.", e);
            response.setHeader("rtnCode", TxnRtnCode.TXN_EXECUTE_FAILED.getCode());
            return;
        }

        //���������߼�����ǰ��鷵�ر����еĵ����Ƿ�����������һ��
        if (!tia.getBillNo().equals(paymentInfo.getBillNo())) {
            assembleAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, "���󵥺����������Ӧ���Ų���.", response);
            logger.info("===��ɫƽ̨��Ӧ����(�쳣ҵ����Ϣ��)��\n" + new String(response.getResponseBody(), response.getCharacterEncoding()));
            return;
        }

        //���������߼�����
        try {
            processTxn(paymentInfo, paymentItems, request);
        } catch (Exception e) {
            assembleAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, e.getMessage(), response);
            logger.error("ҵ����ʧ��.", e);
        }

        //==��ɫƽ̨��Ӧ==
        try {
            starringRespMsg = getRespMsgForStarring(paymentInfo, paymentItems);
            response.setHeader("rtnCode", TxnRtnCode.TXN_EXECUTE_SECCESS.getCode());
            response.setResponseBody(starringRespMsg.getBytes(response.getCharacterEncoding()));
        } catch (Exception e) {
            logger.error("��ɫƽ̨��Ӧ���Ĵ���ʧ��.", e);
            throw new RuntimeException(e);
        }
    }


    //����Starring������
    private CbsTia4010 getCbsTia(byte[] body) throws Exception {
        CbsTia4010 tia = new CbsTia4010();
        SeperatedTextDataFormat starringDataFormat = new SeperatedTextDataFormat(tia.getClass().getPackage().getName());
        tia = (CbsTia4010) starringDataFormat.fromMessage(new String(body, "GBK"), "CbsTia4010");
        return tia;
    }

    //���ɵ����������Ķ�ӦBEAN
    private TpsTia assembleTpsRequestBean(CbsTia4010 cbstia, Stdp10ProcessorRequest request) {
        TpsTia2401 tpstia = new TpsTia2401();
        TpsTia2401.BodyRecord record = ((TpsTia2401.Body) tpstia.getBody()).getObject().getRecord();
        FbiBeanUtils.copyProperties(cbstia, record, true);

        generateTpsBizMsgHeader(tpstia, "2401", request);
        return tpstia;
    }


    //������������ͨѶ
/*
    private TpsToaXmlBean sendAndRecvForTps(byte[] sendTpsBuf, String txnCode) throws Exception {
        byte[] recvBuf = processThirdPartyServer(sendTpsBuf, txnCode);
        logger.info("���������������ر��ģ�\n" + new String(recvBuf, "GBK"));

        return transXmlToBeanForTps(recvBuf);
    }
*/


    //����CBS��Ӧ����
    private String getRespMsgForStarring(FsKfqPaymentInfo paymentInfo, List<FsKfqPaymentItem> paymentItems) {
        CbsToa4010 cbsToa = new CbsToa4010();
        FbiBeanUtils.copyProperties(paymentInfo, cbsToa);

        List<CbsToa4010Item> cbsToaItems = new ArrayList<>();
        for (FsKfqPaymentItem paymentItem : paymentItems) {
            CbsToa4010Item cbsToaItem = new CbsToa4010Item();
            FbiBeanUtils.copyProperties(paymentItem, cbsToaItem);
            cbsToaItems.add(cbsToaItem);
        }
        cbsToa.setItems(cbsToaItems);
        cbsToa.setItemNum("" + cbsToaItems.size());

        String starringRespMsg = "";
        Map<String, Object> modelObjectsMap = new HashMap<String, Object>();
        modelObjectsMap.put(cbsToa.getClass().getName(), cbsToa);
        SeperatedTextDataFormat starringDataFormat = new SeperatedTextDataFormat(cbsToa.getClass().getPackage().getName());
        try {
            starringRespMsg = (String) starringDataFormat.toMessage(modelObjectsMap);
        } catch (Exception e) {
            throw new RuntimeException("��ɫƽ̨����ת��ʧ��.", e);
        }
        return starringRespMsg;
    }

    //=======ҵ���߼�����=================================================
    //����δ�����Ľɿ��¼
    private FsKfqPaymentInfo selectNotCanceledPaymentInfoFromDB(String billNo) {
        SqlSessionFactory sqlSessionFactory = MybatisFactory.ORACLE.getInstance();
        FsKfqPaymentInfoMapper mapper;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            mapper = session.getMapper(FsKfqPaymentInfoMapper.class);
            FsKfqPaymentInfoExample example = new FsKfqPaymentInfoExample();
            example.createCriteria()
                    .andBillNoEqualTo(billNo)
                    .andLnkBillStatusNotEqualTo(BillStatus.CANCELED.getCode());
            List<FsKfqPaymentInfo> infos = mapper.selectByExample(example);
            if (infos.size() == 0) {
                return null;
            }
            if (infos.size() != 1) { //ͬһ���ɿ�ţ�δ�������ڱ���ֻ�ܴ���һ����¼
                throw new RuntimeException("��¼״̬����.");
            }
            return infos.get(0);
        }
    }

    private List<FsKfqPaymentItem> selectPaymentItemsFromDB(FsKfqPaymentInfo paymentInfo) {
        SqlSessionFactory sqlSessionFactory = MybatisFactory.ORACLE.getInstance();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FsKfqPaymentItemExample example = new FsKfqPaymentItemExample();
            example.createCriteria().andChrIdEqualTo(paymentInfo.getChrId());
            FsKfqPaymentItemMapper infoMapper = session.getMapper(FsKfqPaymentItemMapper.class);
            return infoMapper.selectByExample(example);
        }
    }


    private void processTxn(FsKfqPaymentInfo paymentInfo, List<FsKfqPaymentItem> paymentItems, Stdp10ProcessorRequest request) {
        SqlSessionFactory sqlSessionFactory = MybatisFactory.ORACLE.getInstance();
        SqlSession session = sqlSessionFactory.openSession();
        try {
            paymentInfo.setBankIndate(request.getHeader("txnTime").substring(0, 8));
            paymentInfo.setBusinessId(request.getHeader("serialNo"));
            paymentInfo.setOperInitBankid(request.getHeader("branchId"));
            paymentInfo.setOperInitTlrid(request.getHeader("tellerId"));
            paymentInfo.setOperInitDate(new SimpleDateFormat("yyyyMMdd").format(new Date()));
            paymentInfo.setOperInitTime(new SimpleDateFormat("HHmmss").format(new Date()));

            paymentInfo.setArchiveFlag("0");

            paymentInfo.setHostBookFlag("0");
            paymentInfo.setHostChkFlag("0");
            paymentInfo.setFbBookFlag("0");
            paymentInfo.setFbChkFlag("0");

            paymentInfo.setAreaCode("KaiFaQu-FeiShui");
            paymentInfo.setHostAckFlag("0");
            paymentInfo.setLnkBillStatus("0"); //��ʼ��

            FsKfqPaymentInfoMapper infoMapper = session.getMapper(FsKfqPaymentInfoMapper.class);
            infoMapper.insert(paymentInfo);

            FsKfqPaymentItemMapper itemMapper = session.getMapper(FsKfqPaymentItemMapper.class);
            int i = 0;
            for (FsKfqPaymentItem item : paymentItems) {
                i++;
                itemMapper.insert(item);
            }
            session.commit();
        } catch (Exception e) {
            session.rollback();
            throw new RuntimeException("ҵ���߼�����ʧ�ܡ�", e);
        } finally {
            session.close();
        }
    }

}