package com.qianwen.demo.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatSseParserJavaTest {
    @Test
    public void parsesDeltaEventFromSseLines() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: delta");
        parser.parseLine("data: {\"type\":\"delta\",\"messageId\":\"m-1\",\"conversationId\":\"c-1\",\"delta\":\"你好\"}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_DELTA, event.type);
        assertTrue(event.isDelta());
        assertFalse(event.isTerminal());
        assertEquals("m-1", event.messageId);
        assertEquals("c-1", event.conversationId);
        assertEquals("你好", event.delta);
    }

    @Test
    public void parsesMessageEventWhenTypeComesFromEventHeader() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: message");
        parser.parseLine("data: {\"message\":{\"id\":\"m-2\",\"conversationId\":\"c-2\",\"role\":\"assistant\",\"content\":\"ok\",\"status\":\"sent\",\"createdAt\":\"2026-05-14T00:00:00.000Z\",\"updatedAt\":\"2026-05-14T00:00:00.000Z\"}}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_MESSAGE, event.type);
        assertEquals("m-2", event.message.id);
        assertEquals("assistant", event.message.role);
        assertEquals("ok", event.message.content);
    }

    @Test
    public void flushReturnsNullWhenNoPendingFrame() {
        ChatSseParserJava parser = new ChatSseParserJava();
        assertNull(parser.flush());
    }

    @Test
    public void parsesDoneEvent() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: done");
        parser.parseLine("data: {\"type\":\"done\",\"message\":{\"id\":\"m-3\",\"conversationId\":\"c-3\",\"role\":\"assistant\",\"content\":\"完成\",\"status\":\"sent\",\"createdAt\":\"2026-05-14T00:00:00.000Z\",\"updatedAt\":\"2026-05-14T00:00:00.000Z\"}}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_DONE, event.type);
        assertTrue(event.isTerminal());
        assertEquals("m-3", event.message.id);
        assertEquals("完成", event.message.content);
        assertEquals("sent", event.message.status);
    }

    @Test
    public void parsesErrorEvent() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: error");
        parser.parseLine("data: {\"type\":\"error\",\"messageId\":\"m-4\",\"conversationId\":\"c-4\",\"error\":\"模型失败\"}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_ERROR, event.type);
        assertTrue(event.isTerminal());
        assertTrue(event.isError());
        assertEquals("m-4", event.messageId);
        assertEquals("c-4", event.conversationId);
        assertEquals("模型失败", event.error);
    }

    @Test
    public void parsesMultilineDataFrame() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: delta");
        parser.parseLine("data: {\"type\":\"delta\",");
        parser.parseLine("data: \"messageId\":\"m-5\",\"conversationId\":\"c-5\",\"delta\":\"多行\"}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_DELTA, event.type);
        assertEquals("m-5", event.messageId);
        assertEquals("c-5", event.conversationId);
        assertEquals("多行", event.delta);
    }

    @Test
    public void malformedFrameReturnsErrorEventInsteadOfThrowing() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: delta");
        parser.parseLine("data: {\"type\":\"delta\"");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_ERROR, event.type);
        assertTrue(event.error.contains("SSE 解析失败"));
    }

    @Test
    public void ignoresSseCommentLines() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine(": keep-alive");
        parser.parseLine("event: error");
        parser.parseLine("data: {\"type\":\"error\",\"error\":\"连接中断\"}");

        ChatStreamEvent event = parser.parseLine("");
        assertEquals(ChatStreamEvent.TYPE_ERROR, event.type);
        assertEquals("连接中断", event.error);
    }

    @Test
    public void ignoresUnknownFrameType() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("event: heartbeat");
        parser.parseLine("data: {\"foo\":\"bar\"}");

        assertNull(parser.parseLine(""));
    }

    @Test
    public void ignoresDoneSentinelPayload() {
        ChatSseParserJava parser = new ChatSseParserJava();
        parser.parseLine("data: [DONE]");

        assertNull(parser.parseLine(""));
    }
}
