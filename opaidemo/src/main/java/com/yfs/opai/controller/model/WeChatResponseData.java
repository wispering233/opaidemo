package com.yfs.opai.controller.model;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * @JacksonXmlRootElement： 用在类上，用来自定义根节点名称；
 *
 * @JacksonXmlProperty： 用在属性上，用来自定义子节点名称；
 *
 * @JacksonXmlElementWrapper： 用在属性上，可以用来嵌套包装一层父节点，或者禁用此属性参与 XML 转换。
 */
@JacksonXmlRootElement(localName = "xml")
@Data
public class WeChatResponseData {

    @JacksonXmlCData()
    @JacksonXmlProperty(localName = "ToUserName")
    private String toUserName;
    @JacksonXmlCData()
    @JacksonXmlProperty(localName = "FromUserName")
    private String fromUserName;
    @JacksonXmlProperty(localName = "CreateTime")
    private long ctime;
    @JacksonXmlCData()
    @JacksonXmlProperty(localName = "MsgType")
    private String msgType;
    @JacksonXmlCData()
    @JacksonXmlProperty(localName = "Content")
    private String content;



}
