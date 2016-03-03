package com.sogou.pay.common.utils;

import com.sogou.pay.common.types.PMap;
import com.thoughtworks.xstream.XStream;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

/**
 * xml转换工具
 * User: xiepeidong@sogou-inc.com
 * Date: 2016-01-28
 */
public class XMLUtil {

    private static final XStream xstream;
    private static final OutputFormat format;

    static {
        xstream = new XStream();
        format = new OutputFormat();
        format.setSuppressDeclaration(true);
        //format.setIndent(true);
        //format.setNewlines(true);
    }

    private static String Document2String(Document document) {
        Writer writer = new StringWriter();
        XMLWriter xmlWriter = new XMLWriter(writer, format);
        try {
            xmlWriter.write(document);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return writer.toString();
    }

    private static boolean List2XML(Element rootElement, String name, List list) {
        if (rootElement == null || StringUtil.isEmpty(name) || list == null) {
            return false;
        }
        Element element;
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (value == null) {
                element = rootElement.addElement(name);
                element.setText(StringUtil.EMPTY_STRING);
            } else if (value instanceof Map) {
                element = rootElement.addElement(name);
                Map2XML(element, (Map<String, Object>) value);
            } else if (value instanceof List) {
                List2XML(rootElement, name, (List) value);
            } else {
                element = rootElement.addElement(name);
                element.setText(value.toString());
            }
        }
        return true;
    }

    private static boolean Map2XML(Element rootElement, Map<String, Object> map) {
        if (rootElement == null || map == null) {
            return false;
        }
        Element element;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                element = rootElement.addElement(entry.getKey());
                element.setText(StringUtil.EMPTY_STRING);
            } else if (value instanceof Map) {
                element = rootElement.addElement(entry.getKey());
                Map2XML(element, (Map<String, Object>) value);
            } else if (value instanceof List) {
                List2XML(rootElement, entry.getKey(), (List) value);
            } else {
                element = rootElement.addElement(entry.getKey());
                element.setText(value.toString());
            }
        }
        return true;
    }

    public static String Bean2XML(Object bean) {
        return xstream.toXML(bean);
    }

    public static <T> T XML2Bean(final String xml, final Class<T> type) {
        try {
            xstream.alias(type.getSimpleName(), type);
            Document document = DocumentHelper.parseText(xml);
            document.getRootElement().setName(type.getSimpleName());
            String newXml = Document2String(document);
            return (T) xstream.fromXML(newXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String Map2XML(String rootNode, Map map) {
        Document document = DocumentHelper.createDocument();
        Element element = document.addElement(rootNode);
        Map2XML(element, map);
        String xml = Document2String(document);
        return xml;
    }


    private static <T> T XML2Map(Element rootElement, final Class<T> type) {
        if (rootElement == null || rootElement.isTextOnly()) {
            return null;
        }
        Map map = null;
        try {
            map = (Map) type.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        Iterator iterator = rootElement.elementIterator();
        while (iterator.hasNext()) {
            Element element = (Element) iterator.next();
            String key = element.getName();
            if (element.isTextOnly()) {
                String value = element.getText();
                Object old = map.get(key);
                if (old == null)
                    map.put(key, value);
                else if (old instanceof List) {
                    ((List) old).add(value);
                } else {
                    List list = new ArrayList<>();
                    list.add(old);
                    list.add(value);
                    map.put(key, list);
                }
            } else {
                map.put(key, XML2Map(element, type));
            }
        }
        return (T) map;
    }

    private static <T> T XML2Map(String xml, final Class<T> type) {
        try {
            Document document = DocumentHelper.parseText(xml);
            Element element = document.getRootElement();
            return XML2Map(element, type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map XML2Map(String xml) {
        return XML2Map(xml, HashMap.class);
    }

    public static PMap XML2PMap(String xml) {
        return XML2Map(xml, PMap.class);
    }

    public static String JSON2XML(String json) {
        Map map = JSONUtil.JSON2Map(json);
        String xml = Map2XML("root", map);
        return xml;
    }

    public static String XML2JSON(String xml) {
        Map map = XML2Map(xml);
        String json = JSONUtil.Map2JSON(map);
        return json;
    }

    public static class A {
        private int a1;
        private int c1;
        private int b1;

        public int getA1() {
            return a1;
        }

        public void setA1(int a2) {
            this.a1 = a2;
        }

        public int getC1() {
            return c1;
        }

        public void setC1(int c2) {
            this.c1 = c2;
        }

        public int getB1() {
            return b1;
        }

        public void setB1(int b2) {
            this.b1 = b2;
        }
    }


    public static void main(String[] args) {
        HashMap<String, Object> map1 = new HashMap<String, Object>();
        HashMap<String, Object> map11 = new HashMap<>();
        map1.put("a1", 200);
        map1.put("c1", 300);

/*        String xml = Map2XML("root", map1);
        System.out.println(xml);
        PMap map2 = XML2PMap(xml);
        xml = Map2XML("lala", map2);
        System.out.println(xml);
        String json = XML2JSON(xml);
        System.out.println(json);
        xml = JSON2XML(json);
        System.out.println(xml);*/
        return;
    }
}
