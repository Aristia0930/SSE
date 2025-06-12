package org.example.sse;

class SseMessage {
    private String id;
    private String data;
    private String senderId;

    public SseMessage(String id, String data,String senderId) {
        this.id = id;
        this.data = data;
        this.senderId=senderId;
    }
    public String getId() { return id; }
    public String getData() { return data; }
    public String getSenderId() {return senderId;}
}