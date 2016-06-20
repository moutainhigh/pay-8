package com.sogou.pay.common.http.utils;


import com.sogou.pay.common.types.ResultMap;
import com.sogou.pay.common.types.ResultStatus;
import com.sogou.pay.common.utils.MapUtil;
import com.sogou.pay.common.utils.StringUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.perf4j.aop.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Map;
import java.util.Set;

/**
 * User: Liwei
 * Date: 15/3/13
 * Time: 下午4:31
 * Description:
 */
public final class HttpUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

    public static final String DEFAULT_CHARSET = "UTF-8";


    public static String packHttpGetUrl(String rUrl, Map params) {
        return packGetUrl(rUrl, params, new String[]{"https", "http"});
    }

    public static String packHttpsGetUrl(String rUrl, Map params) {
        return packGetUrl(rUrl, params, new String[]{"https"});
    }

    private static String packGetUrl(String rUrl, Map params, String[] protocols) {
        URL url;
        try {
            url = new URL(rUrl);
            boolean isLegalUrl = false;
            for (String proto : protocols) {
                if (proto.equals(url.getProtocol())) {
                    isLegalUrl = true;
                    break;
                }
            }
            if (ArrayUtils.isNotEmpty(protocols) && !isLegalUrl) {
                throw new IllegalArgumentException("Not https URL");
            }

            String newUrl = url.toString();
            if (url.getQuery() != null) {
                newUrl = newUrl.substring(0, newUrl.length() - url.getQuery().length() - 1);
            }

            if (MapUtil.isEmpty(params)) {
                return newUrl;
            } else {
                return newUrl + "?" + packUrlParams(params);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL parse error");
        }
    }

    public static String packUrlParams(Map params) {
        if (MapUtil.isEmpty(params))
            return "";
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) params.entrySet()) {
                String key = (String) entry.getKey();
                Object value = entry.getValue();
                if (StringUtil.isBlank(key) || value == null) {
                    continue;
                }
                sb.append(URLEncoder.encode(key, HttpUtil.DEFAULT_CHARSET));
                sb.append("=");
                sb.append(URLEncoder.encode(value.toString(), HttpUtil.DEFAULT_CHARSET));
                sb.append("&");
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("packUrlParams error");
        }
    }

    public static String urlEncode(String content) {
        try {
            return URLEncoder.encode(content, HttpUtil.DEFAULT_CHARSET);
        } catch (Exception e) {
            throw new IllegalArgumentException("urlEncode error");
        }
    }

    public static String urlDecode(String content) {
        try {
            return URLEncoder.encode(content, HttpUtil.DEFAULT_CHARSET);
        } catch (Exception e) {
            throw new IllegalArgumentException("urlEncode error");
        }
    }

    public static ResultMap extractParams(String params) {
        ResultMap result = ResultMap.build();
        try {
            String[] pairs = params.split("&");
            for (String pair : pairs) {
                String[] param = pair.split("=");
                result.addItem(param[0], param[1]);
            }
        } catch (Exception ex) {
            result.withError(ResultStatus.SYSTEM_ERROR);
        }
        return result;
    }

    public static ResultMap extractUrlParams(String params) {
        ResultMap result = ResultMap.build();
        try {
            String[] pairs = params.split("&");
            for (String pair : pairs) {
                String[] param = pair.split("=");
                result.addItem(urlDecode(param[0]), urlDecode(param[1]));
            }
        } catch (Exception ex) {
            result.withError(ResultStatus.SYSTEM_ERROR);
        }
        return result;
    }
}
