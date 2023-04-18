package com.yfs.opai.controller;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONConverter;
import cn.hutool.json.JSONUtil;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import com.yfs.opai.controller.model.Message;
import com.yfs.opai.controller.model.WeChatResponseData;
import com.yfs.opai.controller.utils.RedisUtil;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


@RequestMapping("wechat")
@RestController
@Slf4j
public class WechatController {
    OpenAiService openAiService = new OpenAiService("sk-****");
    @Autowired
    private RedisUtil redisUtil;


    @GetMapping("save")
    public String save(String fromUserName, String content){
        String roleType ;
        final List<ChatMessage> messages = new ArrayList<>();
        if (!redisUtil.hasKey(fromUserName)){
            roleType = ChatMessageRole.SYSTEM.value();
        }else {
            roleType = ChatMessageRole.USER.value();
            List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
            for (String s : range) {
                JSON json = JSONUtil.parse(s);
                messages.add(new ChatMessage(json.getByPath("role",String.class),json.getByPath("content",String.class)));
            }
        }
        ChatMessage newQuestion = new ChatMessage(roleType, content);
        messages.add(newQuestion);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(1000)
                .logitBias(new HashMap<>())
                .build();
        List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
        String resultContent = choices.get(0).getMessage().getContent();
        redisUtil.rightPush(fromUserName,JSONUtil.toJsonStr(newQuestion));
        redisUtil.rightPush(fromUserName,JSONUtil.toJsonStr(new ChatMessage(ChatMessageRole.ASSISTANT.value(), resultContent)));
        List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
        redisUtil.expire(fromUserName,30*60);
        return  JSONUtil.toJsonStr(range);
    }


    @SneakyThrows
    @RequestMapping("readiness")
    public String wechat(String content) {

        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.USER.value(), content);
        messages.add(systemMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(100)
                .logitBias(new HashMap<>())
                .build();
        List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
        return choices.get(0).getMessage().getContent();

    }


    @SneakyThrows
    @RequestMapping(value = "/cat", produces = MediaType.APPLICATION_XML_VALUE)
    public WeChatResponseData cat(HttpServletRequest httpServletRequest) {
        WeChatResponseData weChatResponseData = new WeChatResponseData();
        weChatResponseData.setCtime(LocalTime.now().toEpochSecond(LocalDate.now(), ZoneOffset.of("+8")));
        weChatResponseData.setMsgType("text");
        weChatResponseData.setFromUserName("gh_9c1f00a5353b");
        String fromUserName = "";
        try (ServletInputStream inputStream = httpServletRequest.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            NodeList nodeList = doc.getElementsByTagName("xml").item(0).getChildNodes();
            String toUserName = nodeList.item(1).getTextContent();
             fromUserName = nodeList.item(3).getTextContent();
            String createTime = nodeList.item(5).getTextContent();
            String msgType = nodeList.item(7).getTextContent();
            String content = nodeList.item(9).getTextContent();
            String msgId = nodeList.item(11).getTextContent();
            String roleType ;
            final List<ChatMessage> messages = new ArrayList<>();
            if (!redisUtil.hasKey(fromUserName)){
                roleType = ChatMessageRole.SYSTEM.value();
            }else {
                roleType = ChatMessageRole.USER.value();
                List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
                for (String s : range) {
                    JSON json = JSONUtil.parse(s);
                    messages.add(new ChatMessage(json.getByPath("role",String.class),json.getByPath("content",String.class)));
                }
            }
            ChatMessage newQuestion = new ChatMessage(roleType, content);
            messages.add(newQuestion);


            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .n(1)
                    .maxTokens(250)
                    .logitBias(new HashMap<>())
                    .build();
            List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
            String resultContent = choices.get(0).getMessage().getContent();
            weChatResponseData.setContent(resultContent);
            weChatResponseData.setToUserName(fromUserName);
            redisUtil.rightPush(fromUserName,JSONUtil.toJsonStr(newQuestion));
            redisUtil.rightPush(fromUserName,JSONUtil.toJsonStr(new ChatMessage(ChatMessageRole.ASSISTANT.value(), resultContent)));
            redisUtil.expire(fromUserName,30*60);
            return weChatResponseData;
        } catch (Exception e) {
            log.warn("失敗{},{}", e, fromUserName);
            weChatResponseData.setContent("ok");
            return weChatResponseData;
        }
    }

    @RequestMapping("proxy")
    public String proxy(){
        OpenAiService service = CustomOkHttpClient.getService();
        Message message = new Message();
        message.setRole("user");
        message.setContent("proxy");
        List<ChatMessage> messages = new ArrayList<>();
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(250)
                .logitBias(new HashMap<>())
                .build();
        List<ChatCompletionChoice> res = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
        return  JSONUtil.toJsonStr(res);

    }



}
