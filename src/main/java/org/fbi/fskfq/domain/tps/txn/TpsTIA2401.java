package org.fbi.fskfq.domain.tps.txn;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.fbi.fskfq.domain.tps.base.TpsTia;
import org.fbi.fskfq.domain.tps.base.TpsTiaBody;
import org.fbi.fskfq.domain.tps.base.TpsTiaHeader;
import org.fbi.fskfq.domain.tps.base.TpsTiaSigns;

import java.io.Serializable;

/**
 * Ӧ�����ݲ�ѯ����
 */

@XStreamAlias("Root")
public class TpsTia2401 extends TpsTia {
    public TpsTiaHeader Head = new TpsTiaHeader();
    public Body Body = new Body();
    public TpsTiaSigns Signs = new TpsTiaSigns();

    @Override
    public TpsTiaHeader getHeader() {
        return Head;
    }

    @Override
    public TpsTiaBody getBody() {
        return Body;
    }

    @Override
    public TpsTiaSigns getSigns() {
        return Signs;
    }

    public static class Body extends TpsTiaBody {
        public Object Object = new Object();
    }

    public static class Object implements Serializable {
        public Record Record = new Record();
    }


    /*
    billtype_code	�ɿ�����ʽ����
bill_no	Ʊ��
verify_no	ȫƱ��У����
bill_money	�տ���
set_year	���
     */
    public static class Record implements Serializable {

        public String billtype_code = "";
        public String bill_no = "";
        public String verify_no = "";
        public String bill_money = "";
        public String set_year = "";
    }

    @Override
    public String toString() {

/*
        Head.dataType = "2401";
        if (StringUtils.isEmpty(Head.msgId)) {
            Head.msgId = new SimpleDateFormat("yyyyMMddHHmmsssss").format(new Date());
        }
        Head.msgRef = Head.msgId;
        if (StringUtils.isEmpty(Head.workDate)) {
            Head.workDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        }
*/

        XmlFriendlyNameCoder replacer = new XmlFriendlyNameCoder("$", "_");
        HierarchicalStreamDriver hierarchicalStreamDriver = new XppDriver(replacer);
        XStream xs = new XStream(hierarchicalStreamDriver);
        xs.processAnnotations(TpsTia2401.class);
        return "<?xml version=\"1.0\" encoding=\"GBK\"?>" + "\n" + xs.toXML(this);
    }
}