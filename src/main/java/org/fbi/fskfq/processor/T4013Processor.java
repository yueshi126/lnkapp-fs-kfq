package org.fbi.fskfq.processor;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.fbi.fskfq.domain.cbs.T4013Request.CbsTia4013;
import org.fbi.fskfq.domain.cbs.T4013Request.CbsTia4013Item;
import org.fbi.fskfq.domain.tps.base.TpsTia;
import org.fbi.fskfq.domain.tps.base.TpsToaXmlBean;
import org.fbi.fskfq.domain.tps.txn.TpsTia2457;
import org.fbi.fskfq.domain.tps.txn.TpsToa9000;
import org.fbi.fskfq.domain.tps.txn.TpsToa9910;
import org.fbi.fskfq.enums.BillStatus;
import org.fbi.fskfq.enums.TxnRtnCode;
import org.fbi.fskfq.helper.FbiBeanUtils;
import org.fbi.fskfq.helper.MybatisFactory;
import org.fbi.fskfq.repository.dao.FsKfqPaymentInfoMapper;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfo;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfoExample;
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
 * Created by zhanrui on 13-12-31.
 * 手工票交易
 */
public class T4013Processor extends AbstractTxnProcessor {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public void doRequest(Stdp10ProcessorRequest request, Stdp10ProcessorResponse response) throws ProcessorException, IOException {
        CbsTia4013 cbsTia;
        try {
            cbsTia = getCbsTia(request.getRequestBody());
        } catch (Exception e) {
            logger.error("特色业务平台请求报文解析错误.", e);
            response.setHeader("rtnCode", TxnRtnCode.CBSMSG_UNMARSHAL_FAILED.getCode());
            return;
        }

        //检查本地数据库信息
        FsKfqPaymentInfo paymentInfo = selectNotCanceledPaymentInfoFromDB(cbsTia.getBillNo());
        if (paymentInfo != null) {
            String billStatus = paymentInfo.getLnkBillStatus();
            if (billStatus.equals(BillStatus.PAYOFF.getCode())) { //已缴款
                response.setHeader("rtnCode", TxnRtnCode.TXN_PAY_REPEATED.getCode());
                logger.info("===此笔缴款单已缴款.");
                return;
            }else if (!billStatus.equals(BillStatus.INIT.getCode())) {  //非初始状态
                response.setHeader("rtnCode", TxnRtnCode.TXN_PAY_REPEATED.getCode());
                logger.info("===此笔缴款单状态错误.");
                return;
            }
        }

        //第三方处理
        TpsToaXmlBean tpsToa = processTpsTx(cbsTia, request, response);
        if (tpsToa == null) { //出现异常
            return;
        }
        //判断正误
        String result = tpsToa.getMaininfoMap().get("RESULT");
        if (result != null) { //异常业务报文
            TpsToa9000 tpsToa9000 = new TpsToa9000();
            try {
                FbiBeanUtils.copyProperties(tpsToa.getMaininfoMap(), tpsToa9000, true);
                marshalAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, tpsToa9000.getAddWord(), response);
            } catch (Exception e) {
                logger.error("第三方服务器响应报文解析异常.", e);
                response.setHeader("rtnCode", TxnRtnCode.TXN_EXECUTE_FAILED.getCode());
            }
        } else { //正常交易逻辑处理
            try {
                String rtnStatus = tpsToa.getMaininfoMap().get("SUCC_CODE");
                String bill_no = tpsToa.getMaininfoMap().get("BILL_NO");
                if (!cbsTia.getBillNo().equals(bill_no)) {
                    marshalAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, "单号不符！", response);
                } else {
                    if (!"OK".equals(rtnStatus)) {
                        marshalAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, rtnStatus, response);
                    } else {
                        processTxn(cbsTia, request, tpsToa);
                        marshalSuccessTxnCbsResponse(response);
                    }
                }
            } catch (Exception e) {
                marshalAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, e.getMessage(), response);
                logger.error("业务处理失败.", e);
            }
        }

    }

    //第三方通讯处理
    private TpsToaXmlBean processTpsTx(CbsTia4013 tia, Stdp10ProcessorRequest request, Stdp10ProcessorResponse response) {
        TpsTia tpsTia = assembleTpsRequestBean(tia, request);
        TpsToaXmlBean tpsToa = new TpsToaXmlBean();

        byte[] sendTpsBuf;
        try {
            sendTpsBuf = generateTpsTxMsgHeader(tpsTia, request);
        } catch (Exception e) {
            logger.error("生成第三方服务器请求报文时出错.", e);
            response.setHeader("rtnCode", TxnRtnCode.TPSMSG_MARSHAL_FAILED.getCode());
            return tpsToa;
        }

        try {
            String dataType = tpsTia.getHeader().getDataType();
            byte[] recvTpsBuf = processThirdPartyServer(sendTpsBuf, dataType);
            String recvTpsMsg = new String(recvTpsBuf, "GBK");

            String rtnDataType = substr(recvTpsMsg, "<dataType>", "</dataType>").trim();
            if ("9910".equals(rtnDataType)) { //技术性异常报文 9910
                TpsToa9910 tpsToa9910 = transXmlToBeanForTps9910(recvTpsBuf);
                //TODO 发起签到交易
                T9905Processor t9905Processor = new T9905Processor();
                t9905Processor.doRequest(request, response);

                logger.info("===第三方服务器返回报文(异常业务信息类)：\n" + tpsToa9910.toString());
                marshalAbnormalCbsResponse(TxnRtnCode.TXN_EXECUTE_FAILED, tpsToa9910.Body.Object.Record.add_word, response);
                return null;
            } else { //业务类正常或异常报文 1402
                tpsToa = transXmlToBeanForTps(recvTpsBuf);
            }
        } catch (SocketTimeoutException e) {
            logger.error("与第三方服务器通讯处理超时.", e);
            response.setHeader("rtnCode", TxnRtnCode.MSG_RECV_TIMEOUT.getCode());
            return null;
        } catch (Exception e) {
            logger.error("与第三方服务器通讯处理异常.", e);
            response.setHeader("rtnCode", TxnRtnCode.MSG_COMM_ERROR.getCode());
            return null;
        }

        return tpsToa;
    }

    //====
    //处理Starring请求报文
    private CbsTia4013 getCbsTia(byte[] body) throws Exception {
        CbsTia4013 tia = new CbsTia4013();
        SeperatedTextDataFormat starringDataFormat = new SeperatedTextDataFormat(tia.getClass().getPackage().getName());
        tia = (CbsTia4013) starringDataFormat.fromMessage(new String(body, "GBK"), "CbsTia4013");
        return tia;
    }

    //查找未撤销的缴款单记录
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
            if (infos.size() != 1) { //同一个缴款单号，未撤销的在表中只能存在一条记录
                throw new RuntimeException("记录状态错误.");
            }
            return infos.get(0);
        }
    }

    //生成第三方请求报文对应BEAN
    private TpsTia assembleTpsRequestBean(CbsTia4013 cbstia, Stdp10ProcessorRequest request) {
        TpsTia2457 tpstia = new TpsTia2457();
        TpsTia2457.BodyRecord record = ((TpsTia2457.Body) tpstia.getBody()).getObject().getRecord();
        FbiBeanUtils.copyProperties(cbstia, record, true);

        List<TpsTia2457.BodyRecord.DetailRecord> detailRecords = new ArrayList<>();
        for (CbsTia4013Item cbsTia4013Item : cbstia.getItems()) {
            TpsTia2457.BodyRecord.DetailRecord   detailRecord = new TpsTia2457.BodyRecord.DetailRecord();
            FbiBeanUtils.copyProperties(cbsTia4013Item, detailRecord, true);
            detailRecords.add(detailRecord);
        }
        record.setObject(detailRecords);
        generateTpsBizMsgHeader(tpstia, "2457", request);
        return tpstia;
    }


    //=============
    private void processTxn(CbsTia4013 cbsTia, Stdp10ProcessorRequest request, TpsToaXmlBean tpsToa) {
        FsKfqPaymentInfo paymentInfo = new FsKfqPaymentInfo();
        FbiBeanUtils.copyProperties(cbsTia, paymentInfo);

        Map<String,String> toaMap = tpsToa.getMaininfoMap();
        paymentInfo.setChrId(toaMap.get("CHR_ID"));
        paymentInfo.setBilltypeCode(toaMap.get("BILLTYPE_CODE"));
        paymentInfo.setBilltypeName(toaMap.get("BILLTYPE_NAME"));
        paymentInfo.setVerifyNo(toaMap.get("VERIFY_NO"));
        paymentInfo.setMakedate(toaMap.get("MAKEDATE"));
        paymentInfo.setIenCode(toaMap.get("IEN_CODE"));
        paymentInfo.setIenName(toaMap.get("IEN_NAME"));
        paymentInfo.setSetYear(toaMap.get("SET_YEAR"));

        SqlSessionFactory sqlSessionFactory = MybatisFactory.ORACLE.getInstance();
        SqlSession session = sqlSessionFactory.openSession();
        try {
            //TODO BankIndate、incomingstatus、pm_code 特色业务系统应提供字段内容
            Date date = new SimpleDateFormat("yyyyMMddHHmmss").parse(request.getHeader("txnTime"));
            paymentInfo.setBankIndate(new SimpleDateFormat("yyyy-MM-dd").format(date));

            paymentInfo.setBusinessId(request.getHeader("serialNo"));

            paymentInfo.setOperPayBankid(request.getHeader("branchId"));
            paymentInfo.setOperPayTlrid(request.getHeader("tellerId"));
            paymentInfo.setOperPayDate(new SimpleDateFormat("yyyyMMdd").format(new Date()));
            paymentInfo.setOperPayTime(new SimpleDateFormat("HHmmss").format(new Date()));
            paymentInfo.setOperPayHostsn(request.getHeader("serialNo"));

            paymentInfo.setHostBookFlag("1");
            paymentInfo.setHostChkFlag("0");
            paymentInfo.setFbBookFlag("1");
            paymentInfo.setFbChkFlag("0");

            paymentInfo.setAreaCode("KaiFaQu-FeiShui");
            paymentInfo.setHostAckFlag("0");
            paymentInfo.setLnkBillStatus(BillStatus.PAYOFF.getCode()); //已缴款
            paymentInfo.setManualFlag("1"); //手工票

            paymentInfo.setPkid(UUID.randomUUID().toString());

            FsKfqPaymentInfoMapper infoMapper = session.getMapper(FsKfqPaymentInfoMapper.class);
            infoMapper.insert(paymentInfo);
            session.commit();
        } catch (Exception e) {
            session.rollback();
            throw new RuntimeException("业务逻辑处理失败。", e);
        } finally {
            session.close();
        }
    }

}
