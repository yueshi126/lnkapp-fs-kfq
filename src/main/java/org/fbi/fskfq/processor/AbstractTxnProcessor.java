package org.fbi.fskfq.processor;

import org.apache.commons.lang.StringUtils;
import org.fbi.fskfq.domain.cbs.T9999Response.TOA9999;
import org.fbi.fskfq.domain.tps.base.TpsTia;
import org.fbi.fskfq.domain.tps.base.TpsToa;
import org.fbi.fskfq.domain.tps.txn.TpsToa9000;
import org.fbi.fskfq.domain.tps.txn.TpsToa9910;
import org.fbi.fskfq.helper.ProjectConfigManager;
import org.fbi.fskfq.helper.TpsSocketClient;
import org.fbi.fskfq.internal.AppActivator;
import org.fbi.linking.codec.dataformat.SeperatedTextDataFormat;
import org.fbi.linking.processor.ProcessorException;
import org.fbi.linking.processor.standprotocol10.Stdp10Processor;
import org.fbi.linking.processor.standprotocol10.Stdp10ProcessorRequest;
import org.fbi.linking.processor.standprotocol10.Stdp10ProcessorResponse;
import org.osgi.framework.BundleContext;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * User: zhanrui
 * Date: 13-12-18
 * Time: ����6:16
 */
public abstract class AbstractTxnProcessor extends Stdp10Processor {

    @Override
    public void service(Stdp10ProcessorRequest request, Stdp10ProcessorResponse response) throws ProcessorException, IOException {
        String txnCode = request.getHeader("txnCode");
        String tellerId = request.getHeader("tellerId");
        if (StringUtils.isEmpty(tellerId)) {
            tellerId = "TELLERID";
        }

        try {
            MDC.put("txnCode", txnCode);
            MDC.put("tellerId", tellerId);
            doRequest(request, response);
        } finally {
            MDC.remove("txnCode");
            MDC.remove("tellerId");
        }
    }

    abstract protected void doRequest(Stdp10ProcessorRequest request, Stdp10ProcessorResponse response) throws ProcessorException, IOException;

    protected String getErrorRespMsgForStarring(String errCode) throws Exception {
        TOA9999 toa = new TOA9999();
        //toa.setErrCode(errCode);
        toa.setErrMsg("����ʧ��-" + getRtnMsg(errCode));
        String starringRespMsg;
        Map<String, Object> modelObjectsMap = new HashMap<String, Object>();
        modelObjectsMap.put(toa.getClass().getName(), toa);
        SeperatedTextDataFormat starringDataFormat = new SeperatedTextDataFormat(toa.getClass().getPackage().getName());
        starringRespMsg = (String) starringDataFormat.toMessage(modelObjectsMap);
        return starringRespMsg;
    }

    //���ݷ������ȡ������Ϣ
    private String getRtnMsg(String rtnCode) {
        BundleContext bundleContext = AppActivator.getBundleContext();
        URL url = bundleContext.getBundle().getEntry("rtncode.properties");

        Properties props = new Properties();
        try {
            props.load(url.openConnection().getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("�����������ļ���������", e);
        }
        String property = props.getProperty(rtnCode);
        if (property == null) {
            property = "δ�����Ӧ�Ĵ�����Ϣ(������:" + rtnCode + ")";
        }
        return property;
    }

    //����ͨѶ����ͷ
    protected byte[] generateTxMsg(TpsTia tpstia) throws UnsupportedEncodingException {
        String isSign = "0";
        String authLen = "100";
        String authCode = "10012121212";
        String reserve = "               ";

        String sendTime = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        // �����ĵ��е���������Ϊ������
        String msg = StringUtils.leftPad(tpstia.getHeader().dataType, 6, " ")
                + StringUtils.leftPad(tpstia.getHeader().src, 15, " ")
                + StringUtils.leftPad(tpstia.getHeader().des, 15, " ")
                + sendTime
                + isSign
                + StringUtils.leftPad(authLen, 3, "0")
                + reserve
                + authCode
                + tpstia.toString();

        int msglength = msg.getBytes("GBK").length + 8;
        String len = StringUtils.leftPad("" + msglength, 8, "0");

        byte[] buffer = new byte[msglength];
        System.arraycopy(len.getBytes(), 0, buffer, 0, 8);
        System.arraycopy(msg.getBytes("GBK"), 0, buffer, 8, msglength - 8);

        return buffer;
    }

    //���������������ɸ��ݽ��׺����ò�ͬ�ĳ�ʱʱ��
    protected byte[] processThirdPartyServer(byte[] sendTpsBuf, String txnCode) throws Exception {
        String servIp = ProjectConfigManager.getInstance().getProperty("tps.server.ip");
        int servPort = Integer.parseInt(ProjectConfigManager.getInstance().getProperty("tps.server.port"));
        TpsSocketClient client = new TpsSocketClient(servIp, servPort);

        String timeoutCfg = ProjectConfigManager.getInstance().getProperty("tps.server.timeout.txn." + txnCode);
        if (timeoutCfg != null) {
            int timeout = Integer.parseInt(timeoutCfg);
            client.setTimeout(timeout);
        } else {
            timeoutCfg = ProjectConfigManager.getInstance().getProperty("tps.server.timeout");
            if (timeoutCfg != null) {
                int timeout = Integer.parseInt(timeoutCfg);
                client.setTimeout(timeout);
            }
        }

        return client.call(sendTpsBuf);
    }

    protected TpsToa transXmlToBeanForTps(byte[] buf){
        String txnCode = new String(buf, 0, 6).trim();
        int authLen = Integer.parseInt(new String(buf, 51, 3)) +1;
        String msgdata = new String(buf, 69 + authLen, buf.length - 69 - authLen);
        System.out.println("===�����壺\n" + msgdata);

        TpsToa toa = null;

        if (!"9910".equals(txnCode) && !"9906".equals(txnCode) && !"9000".equals(txnCode)) {
            Class clazz = null;
            try {
                clazz = Class.forName("org.fbi.fskfq.domain.tps.base.TpsToaXmlBean");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            TpsToa tmptoa = null;
            try {
                tmptoa = (TpsToa) clazz.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            toa = tmptoa.toBean(msgdata);
            return toa;
        }

        if ("9000".equals(txnCode)) {
            toa = new TpsToa9000();
            TpsToa9000 toa9000 = (TpsToa9000) toa.toBean(msgdata);
            return toa9000;
            //throw new RuntimeException(toa9000.Body.Object.Record.result + toa9000.Body.Object.Record.add_word);
        } else if ("9910".equals(txnCode)) {
            toa = new TpsToa9910();
            TpsToa9910 toa9910 = (TpsToa9910) toa.toBean(msgdata);
            return toa9910;
            //throw new RuntimeException(toa9910.Body.Object.Record.result + toa9910.Body.Object.Record.add_word);
        } else {
            return toa;
        }
    }
}