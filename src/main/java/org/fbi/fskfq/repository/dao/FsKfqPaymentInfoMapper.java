package org.fbi.fskfq.repository.dao;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfo;
import org.fbi.fskfq.repository.model.FsKfqPaymentInfoExample;

public interface FsKfqPaymentInfoMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int countByExample(FsKfqPaymentInfoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int deleteByExample(FsKfqPaymentInfoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int deleteByPrimaryKey(String chrId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int insert(FsKfqPaymentInfo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int insertSelective(FsKfqPaymentInfo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    List<FsKfqPaymentInfo> selectByExample(FsKfqPaymentInfoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    FsKfqPaymentInfo selectByPrimaryKey(String chrId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int updateByExampleSelective(@Param("record") FsKfqPaymentInfo record, @Param("example") FsKfqPaymentInfoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int updateByExample(@Param("record") FsKfqPaymentInfo record, @Param("example") FsKfqPaymentInfoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int updateByPrimaryKeySelective(FsKfqPaymentInfo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table FIS.FS_KFQ_PAYMENT_INFO
     *
     * @mbggenerated Tue Dec 31 14:44:57 CST 2013
     */
    int updateByPrimaryKey(FsKfqPaymentInfo record);
}