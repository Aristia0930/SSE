package org.example.sse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/sse")
public class SseController {

    @Autowired
    private SseService sseService;


    //최초 연결
    @GetMapping("/subscribe/{userId}")
    public SseEmitter subscribe(@PathVariable String userId) throws IOException {
       return sseService.subscribe(userId);

    }

    //메세지 보내기
    @PostMapping("/notify/{receiverId}")
    public String sendMessage(@PathVariable String receiverId,@RequestParam String message ,@RequestParam String senderId ){
        return sseService.sendMessage(receiverId,message,senderId);

    }


    //채팅방 연결 추가
    @GetMapping("/chat/{roomId}")
    public SseEmitter subscribeRoom(@PathVariable String roomId){
        //룸 연결 하기
    }

}
