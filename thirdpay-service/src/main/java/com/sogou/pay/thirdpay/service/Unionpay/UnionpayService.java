/*
 * $Id$
 *
 * Copyright (c) 2015 Sogou.com. All Rights Reserved.
 */
package com.sogou.pay.thirdpay.service.Unionpay;

import com.google.common.collect.ImmutableMap;
import com.sogou.pay.common.enums.OrderStatus;
import com.sogou.pay.common.exception.ServiceException;
import com.sogou.pay.common.http.client.HttpService;
import com.sogou.pay.common.http.utils.HttpUtil;
import com.sogou.pay.common.types.PMap;
import com.sogou.pay.common.types.Result;
import com.sogou.pay.common.types.ResultMap;
import com.sogou.pay.common.types.ResultStatus;
import com.sogou.pay.common.utils.DateUtil;
import com.sogou.pay.common.utils.MapUtil;
import com.sogou.pay.thirdpay.biz.enums.UnionpayTxnType;
import com.sogou.pay.thirdpay.biz.utils.SecretKeyUtil;
import com.sogou.pay.thirdpay.service.Tenpay.TenpayUtils;
import com.sogou.pay.thirdpay.service.ThirdpayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

//--------------------- Change Logs----------------------
//@author wangwenlong Initial Created at 2016年6月15日;
//-------------------------------------------------------
public class UnionpayService implements ThirdpayService {

  private static HashMap<String, String> TRADE_STATUS = new HashMap<String, String>();

  static {
    TRADE_STATUS.put("00", OrderStatus.SUCCESS.name());//交易成功结束
    TRADE_STATUS.put("A6", OrderStatus.SUCCESS.name());//交易有缺陷成功
    TRADE_STATUS.put("DEFAULT", OrderStatus.FAILURE.name());//默认
  }

  @Override
  public ResultMap preparePayInfoAccount(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap preparePayInfoGatway(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap preparePayInfoQRCode(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public ResultMap preparePayInfoSDK(PMap params) throws ServiceException {
    ResultMap result = doRequest(params, getPreparePayInfoRequestParams(params, InternalChannelType.sdk));
    if (!Result.isSuccess(result)) {
      LOG.error("[preparePayInfoSDK] failed, params={}", params);
      return result;
    }

    PMap resultPMap = (PMap) result.getItem("resultPMap");
    return result.addItem("token", resultPMap.getString("tn"));
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private PMap<String, String> getPreparePayInfoRequestParams(PMap params, InternalChannelType internalChannelType) {
    PMap<String, String> result = new PMap<>();

    result.put("version", VERSION);
    result.put("encoding", CHARSET);
    result.put("certId", CERTID);
    result.put("signMethod", SIGNMETHOD);
    result.put("txnType", UnionpayTxnType.consumption.getValue());
    result.put("txnSubType", TXNSUBTYPE);
    result.put("bizType", BizType.DEFAULT.getValue());
    result.put("channelType", channelMap.get(internalChannelType));
    result.put("backUrl", params.getString("serverNotifyUrl"));
    result.put("accessType", ACCESSTYPE);
    result.put("merId", params.getString("merchantNo"));
    result.put("orderId", params.getString("serialNumber"));
    result.put("txnTime", LocalDateTime.now().format(FORMATTER));
    result.put("txnAmt", TenpayUtils.fenParseFromYuan(params.getString("orderAmount")));
    result.put("currencyCode", CURRENCYCODE);

    return result;
  }

  @Override
  public ResultMap preparePayInfoWap(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  private ResultMap doRequest(PMap params, PMap requestPMap) throws ServiceException {

    ResultMap result = ResultMap.build();

    if (!MapUtil.checkAllExist(requestPMap)) {
      LOG.error("[doRequest] 银联请求参数错误, 参数: {}", requestPMap);
      return ResultMap.build(ResultStatus.THIRD_REFUND_PARAM_ERROR);
    }
    //获取商户私钥路径
    String privateCertFilePath = "e:" + params.getString("privateCertFilePath");
    //获取商户私钥
    String privateCertKey = SecretKeyUtil.loadKeyFromFile(privateCertFilePath);
    if (privateCertKey.equals("")) {
      LOG.error("[doRequest] 银联请求获取第三方支付账户密钥失败, 参数: {}", privateCertFilePath);
      return ResultMap.build(ResultStatus.THIRD_PAY_GET_KEY_ERROR);
    }
    //签名
    String sign = SecretKeyUtil.unionRSASign(requestPMap, privateCertKey, CHARSET);
    if (sign == null) {
      LOG.error("[doRequest] 银联请求签名失败, 参数: {}", requestPMap);
      return ResultMap.build(ResultStatus.THIRD_PAY_SIGN_ERROR);
    }
    requestPMap.put("signature", sign);

    Result httpResponse = HttpService.getInstance().doPost(params.getString("refundUrl"), requestPMap, CHARSET, null);
    if (httpResponse.getStatus() != ResultStatus.SUCCESS) {
      LOG.error("[doRequest] 银联请求HTTP请求失败, 参数: {}", requestPMap);
      result.withError(ResultStatus.THIRD_REFUND_HTTP_ERROR);
      return result;
    }
    String resContent = (String) httpResponse.getReturnValue();
    //解析响应
    if (resContent == null) {
      LOG.error("[doRequest] 银联请求返回参数异常, 参数: {}", requestPMap);
      result.withError(ResultStatus.THIRD_QUERY_RESPONSE_PARAM_ERROR);
      return result;
    }
    PMap resultPMap = HttpUtil.extractUrlParams(resContent).getData();
    //获取商户私钥路径
    String publicCertFilePath = "e:" + params.getString("publicCertFilePath");
    //获取银联公钥
    String publicCertKey = SecretKeyUtil.loadKeyFromFile(publicCertFilePath);
    if (publicCertKey.equals("")) {
      LOG.error("[doRequest] 银联请求获取银联公钥失败, 参数: {}", publicCertFilePath);
      return ResultMap.build(ResultStatus.THIRD_PAY_GET_KEY_ERROR);
    }
    //验证响应合法性
    sign = (String) resultPMap.remove("signature");
    boolean signOK = SecretKeyUtil.unionRSACheckSign(resultPMap, sign, publicCertKey, CHARSET);
    if (!signOK) {
      LOG.error("[doRequest] 银联请求返回参数签名错误, 参数: {}, 返回: {}", requestPMap, resultPMap);
      result.withError(ResultStatus.THIRD_REFUND_RESPONSE_SIGN_ERROR);
      return result;
    }
    String respCode = resultPMap.getString("respCode");
    if (!respCode.equals("00") && !respCode.equals("A6")) {
      LOG.error("[doRequest] 银联请求返回参数异常, respCode!=00/A6，参数: {}, 返回: {}", requestPMap, resultPMap);
      result.addItem("error_code", respCode);
      result.addItem("error_msg", resultPMap.getString("respMsg"));
      result.withError(ResultStatus.THIRD_REFUND_RESPONSE_PARAM_ERROR);
      return result;
    }
    return result.addItem("resultPMap", resultPMap);
  }

  private ResultMap query(PMap params, String resultName) throws ServiceException {
    PMap requestPMap = new PMap();
    //组装请求参数
    requestPMap.put("version", VERSION); //版本号
    requestPMap.put("encoding", CHARSET); //字符集编码
    requestPMap.put("signMethod", SIGNMETHOD); //签名方法 目前只支持01-RSA方式证书加密
    requestPMap.put("txnType", UnionpayTxnType.trade_inquiry.getValue()); //交易类型 00-查询
    requestPMap.put("txnSubType", TXNSUBTYPE); //交易子类型  默认00
    requestPMap.put("bizType", BizType.B2C_GATEWAY_PAYMENT.getValue()); //业务类型
    requestPMap.put("accessType", ACCESSTYPE); //接入类型，商户接入固定填0，不需修改
    requestPMap.put("merId", params.getString("merchantNo")); //商户号
    requestPMap.put("queryId", params.getString("serialNumber")); //原消费交易返回的的queryId

    ResultMap result = doRequest(params, requestPMap);
    if (!Result.isSuccess(result)) {
      LOG.error("[query] failed, params={}", params);
      return result;
    }

    PMap resultPMap = (PMap) result.getItem("resultPMap");
    return result.addItem(resultName, getTradeStatus(resultPMap.getString("origRespCode")));
  }

  @Override
  public ResultMap queryOrder(PMap params) throws ServiceException {
    return query(params, "order_state");
  }

  @Override
  public ResultMap refundOrder(PMap params) throws ServiceException {
    PMap requestPMap = new PMap();
    //组装请求参数
    requestPMap.put("version", VERSION); //版本号
    requestPMap.put("encoding", CHARSET); //字符集编码
    requestPMap.put("signMethod", SIGNMETHOD); //签名方法 目前只支持01-RSA方式证书加密
    requestPMap.put("txnType", UnionpayTxnType.refund.getValue()); //交易类型 04-退货
    requestPMap.put("txnSubType", TXNSUBTYPE); //交易子类型  默认00
    requestPMap.put("bizType", BizType.B2C_GATEWAY_PAYMENT.getValue()); //业务类型
    requestPMap.put("channelType", ExternalChannelType.mobile.getValue()); //渠道类型，07-PC，08-手机
    requestPMap.put("merId", params.getString("merchantNo")); //商户号
    requestPMap.put("accessType", ACCESSTYPE); //接入类型，商户接入固定填0，不需修改
    requestPMap.put("orderId", params.getString("refundSerialNumber")); //商户退款单号，8-40位数字字母，不能含“-”或“_”
    requestPMap.put("txnTime", DateUtil.format(params.getDate("refundReqTime"), DateUtil.DATE_FORMAT_SECOND_SHORT)); //订单发送时间，格式为YYYYMMDDhhmmss，必须取当前时间，否则会报txnTime无效
    requestPMap.put("currencyCode", CURRENCYCODE); //交易币种（境内商户一般是156 人民币）
    String refundAmount = TenpayUtils.fenParseFromYuan(params.getString("refundAmount"));
    requestPMap.put("txnAmt", refundAmount); //退货金额，单位分
    requestPMap.put("backUrl", params.getString("refundNotifyUrl")); //后台通知地址
    requestPMap.put("origQryId", params.getString("serialNumber")); //原消费交易返回的的queryId

    ResultMap result = doRequest(params, requestPMap);
    if (!Result.isSuccess(result)) {
      LOG.error("[refundOrder] failed, params={}", params);
      return result;
    }

    PMap resultPMap = (PMap) result.getItem("resultPMap");
    return result.addItem("third_refund_id", resultPMap.getString("queryId"));
  }

  @Override
  public ResultMap queryRefundOrder(PMap params) throws ServiceException {
    return query(params, "refund_status");
  }

  @Override
  public ResultMap downloadOrder(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  private String getTradeStatus(String unionpayTradeStatus) {
    if (unionpayTradeStatus == null) return UnionpayService.TRADE_STATUS.get("DEFAULT");
    String trade_status = UnionpayService.TRADE_STATUS.get(unionpayTradeStatus);
    if (trade_status == null) return UnionpayService.TRADE_STATUS.get("DEFAULT");
    return trade_status;
  }

  @Override
  public ResultMap prepareTransferInfo(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifyWebSync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifyWebAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifyWapSync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifyWapAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifySDKAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap getReqIDFromNotifyRefund(PMap params) throws ServiceException {
    ResultMap result = ResultMap.build();
    result.addItem("reqId", params.getString("orderId"));
    return result;
  }

  @Override
  public ResultMap getReqIDFromNotifyTransfer(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifyWebSync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifyWebAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifyWapSync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifyWapAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifySDKAsync(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  @Override
  public ResultMap handleNotifyRefund(PMap params) throws ServiceException {

    ResultMap result = ResultMap.build();
    PMap notifyParams = params.getPMap("data");
    //获取商户私钥路径
    String publicCertFilePath = "e:" + params.getString("publicCertFilePath");
    //获取银联公钥
    String publicCertKey = SecretKeyUtil.loadKeyFromFile(publicCertFilePath);
    if (publicCertKey.equals("")) {
      LOG.error("[handleNotifyRefund] 银联回调获取银联公钥失败, 参数: {}", publicCertFilePath);
      return ResultMap.build(ResultStatus.THIRD_PAY_GET_KEY_ERROR);
    }
    //验证响应合法性
    String sign = (String) notifyParams.remove("signature");
    boolean signOK = SecretKeyUtil.unionRSACheckSign(notifyParams, sign, publicCertKey, CHARSET);
    if (!signOK) {
      LOG.error("[handleNotifyRefund] 银联回调返回参数签名错误, 参数: {}", params);
      result.withError(ResultStatus.THIRD_REFUND_RESPONSE_SIGN_ERROR);
      return result;
    }

    //提取关键参数
    String orderId = notifyParams.getString("orderId");
    String queryId = notifyParams.getString("queryId");
    String txnAmt = notifyParams.getString("txnAmt");
    txnAmt = String.valueOf(new BigDecimal(txnAmt).divide(new BigDecimal(100), 2, BigDecimal.ROUND_UP));
    String respCode = getTradeStatus(notifyParams.getString("respCode"));

    result.addItem("reqId", orderId);//商户网站唯一退款单号
    result.addItem("agencyRefundId", queryId);//第三方退款单号
    result.addItem("refundStatus", respCode);//交易状态
    result.addItem("refundMoney", txnAmt);//支付金额

    return result;
  }

  @Override
  public ResultMap handleNotifyTransfer(PMap params) throws ServiceException {
    // TODO Auto-generated method stub
    return null;

  }

  private void sign(TreeMap<String, String> params) {
    if (params == null || params.isEmpty()) return;
    StringBuilder sb = new StringBuilder();
    params.keySet().forEach(k -> sb.append(k).append('=').append(params.get(k)).append('&'));
    String signString = sb.substring(0, sb.length() - 1);

  }

  private static final Logger LOG = LoggerFactory.getLogger(UnionpayService.class);

  private static String VERSION = "5.0.0";

  private static String CHARSET = "UTF-8";

  private static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("YYYYMMDDHHmmss");

  private static String CERTID;

  private static String ACCESSTYPE = "0";//0 for ordinary merchant 2 for platform merchant

  private static String TXNSUBTYPE = "00";//默认00; 01自助消费,通过地址的方式区分前台消费和后台消费(含无跳转支付); 03分期付款

  private static String CURRENCYCODE = "156";//rmb

  private static String SIGNMETHOD = "01";//rsa

  enum BizType {
    DEFAULT("000000"), //默认
    STOCK_FUND("000101"), //基金业务之股票基金
    MONETARY_FUND("000102"), //基金业务之货币基金
    B2C_GATEWAY_PAYMENT("000201"), //B2C网关支付
    AUTHENTICATION_PAYMENT("000301"), //认证支付2.0
    GRADE_PAYMENT("000302"), //评级支付
    PAYMENT("000401"), //代付
    COLLECTION("000501"), //代收
    BILL_PAYMENT("000601"), //账单支付
    INTERBANK_PAYMENT("000801"), //跨行支付
    BINDING_PAYMENT("000901"), //绑定支付
    ORDER("001001"), //订购
    B2B("000202"); //B2B

    private String value;

    private BizType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

  }

  private static Map<InternalChannelType, String> channelMap = ImmutableMap.of(InternalChannelType.gateway,
      ExternalChannelType.internet.getValue(), InternalChannelType.sdk, ExternalChannelType.mobile.getValue());

  enum InternalChannelType {
    account, gateway, qrcode, sdk, wap;
  }

  enum ExternalChannelType {
    voice("05"), internet("07"), mobile("08");

    private String value;

    private ExternalChannelType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

}
