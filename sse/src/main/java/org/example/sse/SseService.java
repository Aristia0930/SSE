package org.example.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    //유저 저장
    private final ConcurrentHashMap<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    // 사용자별로 이벤트 리스트 저장 (간단히 Map)
    private final Map<String, List<SseMessage>> userMessages = new ConcurrentHashMap<>();

    //맵으로 되어있는것들은 db로 교체 가능

    public SseEmitter subscribe(String userId) throws IOException {
        SseEmitter emitter = new SseEmitter(60 * 1000L); // 타임아웃 1분
        emitterMap.put(userId, emitter); // 사용자 ID로 emitter 등록

        //접속시 이전 메세지 있으면 불러오기
        List<SseMessage> missedMessages = getMessage(userId);

        if(missedMessages!=null) {
            for (SseMessage message : missedMessages) {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .id(message.getId())
                        .data(message.getSenderId() + ":" + message.getData())

                );
            }
        }
        emitter.onCompletion(()-> emitterMap.remove(userId));
        emitter.onTimeout(()->emitterMap.remove(userId));

        //첫 연결시 이벤트 발생
        //초기 메세지 전달
        try{
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("연결성공")

            );
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;


    }


    public String sendMessage(String receiverId, String message,String senderId) {
        String eventId = UUID.randomUUID().toString();
        SseMessage sseMessage=new SseMessage(eventId,message,senderId);
        SseEmitter emitter=emitterMap.get(receiverId);


        if(emitter !=null){
            try{
                saveMessage(receiverId,sseMessage);

                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name("message")
                        .data(senderId+":"+message)

                );
            } catch (IOException e) {
                emitter.completeWithError(e);
                return "전송실패";
            }

            return "전송 성공";
        }else{
            return "사용자 미접속";
        }



    }

    //메세지 저장
    public void saveMessage(String userId, SseMessage message) {
        userMessages.computeIfAbsent(userId, k -> new ArrayList<>()).add(message);
    }

    //자신한테온 메세지 모두 조회
    //만약 특정 이벤트 아이디 이후 모든 메세지를 받고 싶다면 마지막에 보낸 이벤트 아이디 를 저장했다가 사용해야한다.
    public List<SseMessage> getMessage(String userId){
        return userMessages.get(userId);

    }

}
