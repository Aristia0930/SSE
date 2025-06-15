# Spring Boot SSE (Server-Sent Events) 채팅 서비스

이 프로젝트는 Spring Boot를 사용하여 **Server-Sent Events (SSE)**를 활용한 단방향 실시간 채팅 및 알림 서비스를 구현합니다. SSE는 서버에서 클라이언트로 실시간 데이터를 스트리밍하는 데 효과적인 기술입니다.

## 목차
- [SSE란?](#sse란)
- [프로젝트 개요](#프로젝트-개요)
- [핵심 서비스 클래스: SseService](#핵심-서비스-클래스-sseservice)
- [사용자 및 메시지 관리 구조](#사용자-및-메시지-관리-구조)
- [메서드 설명](#메서드-설명)
  - [subscribe(String userId)](#subscribestring-userid)
  - [sendMessage(String receiverId, String message, String senderId)](#sendmessagestring-receiverid-string-message-string-senderid)
  - [saveMessage(String userId, SseMessage message)](#savemessagestring-userid-ssemessage-message)
  - [getMessage(String userId)](#getmessagestring-userid)
- [적용 및 활용 사례](#적용-및-활용-사례)

## SSE란?

**Server-Sent Event (SSE)**는 서버에서 클라이언트로 실시간 데이터를 단방향으로 전송하는 기술입니다. 이는 HTTP의 비연결성 문제를 해결하여 실시간 통신을 위한 방법 중 하나입니다.

### 특징
- **단방향 통신**: 서버에서 클라이언트로만 데이터 전송이 가능합니다.
- **HTTP 기반 통신**: 일반 HTTP 프로토콜을 사용합니다.
- **자동 재연결 지원**: 연결이 끊어지면 클라이언트가 자동으로 재접속을 시도합니다.
- **광범위한 브라우저 지원**: 대부분의 최신 웹 브라우저에서 지원됩니다.
- **GET 메소드만 사용**: 데이터를 요청하는 데에 GET 메소드만 사용합니다.

### 활용 가능 분야
- **실시간 알림 서비스**: 새로운 메시지, 친구 요청 등 실시간 알림을 사용자에게 푸시합니다.
- **라이브 채팅**: 간단한 서버 → 클라이언트 메시지 전송이 필요한 채팅 시스템에 적합합니다.
- **주식/경매 등 실시간 데이터 업데이트**: 주식 가격, 경매 입찰가 등 실시간으로 변동하는 데이터를 클라이언트에 업데이트합니다.

## 프로젝트 개요

본 프로젝트는 Spring Boot 기반으로 SSE를 이용하여 실시간 채팅 및 알림 기능을 제공합니다.

- **사용자 연결 관리**: `SseEmitter` 객체를 사용자별로 관리하여 실시간 연결을 유지합니다.
- **메시지 저장**: 사용자별로 전달된 메시지들을 인메모리 `Map`에 저장하여 메시지 유실을 방지합니다. (실제 서비스에서는 데이터베이스(DB) 사용을 권장합니다.)
- **메시지 전송**: 접속 중인 사용자에게는 실시간으로 메시지를 전송하고, 미접속 사용자의 메시지는 저장 후 재접속 시 전달합니다.

## 핵심 서비스 클래스: SseService

`SseService` 클래스는 SSE를 통한 사용자 연결 관리 및 메시지 전송 로직을 담당합니다.

### 사용자 및 메시지 관리 구조

| 변수명 | 타입 | 역할 |
|--------|------|------|
| `emitterMap` | `ConcurrentHashMap<String, SseEmitter>` | 현재 SSE 연결을 유지 중인 사용자별 `SseEmitter` 객체를 저장합니다. |
| `userMessages` | `Map<String, List<SseMessage>>` | 사용자별로 전달된 메시지들을 저장합니다. (놓친 메시지 전달용) |

> **참고**: `ConcurrentHashMap`을 사용하여 여러 스레드에서 동시에 접근하더라도 동시성 안전을 보장합니다.  
> 실제 서비스에서는 메시지 영속성 및 확장성을 위해 DB를 이용하여 메시지를 저장하고 조회하는 것을 권장합니다.

## 메서드 설명

### subscribe(String userId)

사용자가 SSE 연결을 요청할 때 호출되는 메서드입니다.

```java
public SseEmitter subscribe(String userId) throws IOException {
    SseEmitter emitter = new SseEmitter(60 * 1000L); // 타임아웃 1분
    emitterMap.put(userId, emitter);

    // (1) 이전에 놓친 메시지 전송
    List<SseMessage> missedMessages = getMessage(userId);
    if (missedMessages != null) {
        for (SseMessage message : missedMessages) {
            emitter.send(SseEmitter.event()
                    .name("message") // 이벤트 이름 설정
                    .id(message.getId()) // 이벤트 고유 ID 설정
                    .data(message.getSenderId() + ":" + message.getData()) // 전송 데이터
            );
        }
    }

    // (2) 연결 상태 콜백 처리
    // 연결 완료 시 Map에서 해당 Emitter 제거
    emitter.onCompletion(() -> emitterMap.remove(userId));
    // 타임아웃 시 Map에서 해당 Emitter 제거
    emitter.onTimeout(() -> emitterMap.remove(userId));
    // 에러 발생 시 처리 로직 추가 권장: emitter.onError(e -> ...)

    // (3) 초기 연결 성공 메시지 전송
    try {
        emitter.send(SseEmitter.event()
                .name("connect") // 'connect'라는 이름의 이벤트 전송
                .data("연결성공") // "연결성공" 메시지 포함
        );
    } catch (IOException e) {
        emitter.completeWithError(e); // 메시지 전송 실패 시 연결 종료
    }

    return emitter;
}
```

**주요 동작:**
- 새 `SseEmitter` 객체를 생성하고 `emitterMap`에 저장합니다.
- 사용자에게 전달되지 않은 메시지(`missedMessages`)를 전송하여 메시지 유실을 방지합니다.
- `emitter.onCompletion()` 및 `emitter.onTimeout()` 콜백을 등록하여 연결이 완료되거나 타임아웃될 때 `emitterMap`에서 해당 `SseEmitter`를 제거하여 리소스를 관리합니다.
- 최초 연결 시 "연결성공" 메시지를 `connect` 이벤트로 전송하여 클라이언트가 연결 상태를 확인할 수 있도록 합니다.

### sendMessage(String receiverId, String message, String senderId)

특정 사용자에게 메시지를 전송합니다.

```java
public String sendMessage(String receiverId, String message, String senderId) {
    String eventId = UUID.randomUUID().toString(); // 고유 이벤트 ID 생성
    SseMessage sseMessage = new SseMessage(eventId, message, senderId);
    SseEmitter emitter = emitterMap.get(receiverId); // 수신자의 SseEmitter 조회

    if (emitter != null) { // 수신자가 현재 접속 중인 경우
        try {
            saveMessage(receiverId, sseMessage); // 메시지 저장
            emitter.send(SseEmitter.event()
                    .id(eventId) // 이벤트 고유 ID
                    .name("message") // 'message' 이벤트
                    .data(senderId + ":" + message) // 전송 데이터
            );
        } catch (IOException e) {
            emitter.completeWithError(e); // 전송 실패 시 연결 종료
            return "전송실패";
        }
        return "전송 성공";
    } else { // 수신자가 접속 중이 아닌 경우
        saveMessage(receiverId, sseMessage); // 메시지만 저장 (나중에 전송)
        return "사용자 미접속";
    }
}
```

**주요 동작:**
- 메시지를 전송하기 전에 `saveMessage()`를 호출하여 메시지를 저장합니다. 이는 수신자가 현재 접속 중이 아니더라도 나중에 연결했을 때 메시지를 받을 수 있도록 보장합니다.
- 수신자의 `SseEmitter`를 `emitterMap`에서 조회하여 접속 중이면 실시간으로 메시지를 전송합니다.
- 접속 중이 아니면 "사용자 미접속"을 반환하지만, 메시지는 이미 저장되었으므로 사용자가 재접속 시 받을 수 있습니다.
- 메시지 전송 중 `IOException` 발생 시 해당 `SseEmitter` 연결을 종료하고 오류를 처리합니다.

### saveMessage(String userId, SseMessage message)

사용자별 메시지를 저장합니다.

```java
public void saveMessage(String userId, SseMessage message) {
    // userMessages 맵에서 해당 userId의 메시지 리스트를 조회하거나, 없으면 새로 생성 후 메시지 추가
    userMessages.computeIfAbsent(userId, k -> new ArrayList<>()).add(message);
}
```

**주요 동작:**
- `userMessages` 맵에서 해당 `userId`에 대한 `ArrayList`가 존재하지 않으면 새로 생성하고, 메시지를 추가합니다. 이미 존재하면 기존 리스트에 메시지를 추가합니다.

### getMessage(String userId)

사용자에게 전송된 메시지를 조회합니다.

```java
public List<SseMessage> getMessage(String userId) {
    return userMessages.get(userId);
}
```

**주요 동작:**
- `userMessages` 맵에서 `userId`에 해당하는 메시지 리스트를 반환합니다.

> **참고**: 현재는 모든 메시지를 반환하지만, 실제 서비스에서는 효율성을 위해 페이징 처리 또는 특정 이벤트 ID 이후의 메시지만 조회하는 기능이 필요할 수 있습니다.

## 적용 및 활용 사례

본 SSE 채팅 서비스는 다음과 같은 분야에 적용 및 활용될 수 있습니다:

- **실시간 알림 서비스**: 새로운 메시지, 친구 요청, 공지사항 등 사용자에게 즉각적인 알림을 전달합니다.
- **단순 라이브 채팅 시스템**: 복잡한 양방향 통신보다는 서버에서 클라이언트로 메시지를 푸시하는 형태의 채팅에 적합합니다.
- **실시간 데이터 업데이트**: 주식 가격 변동, 경매 입찰 현황, 스포츠 경기 점수 등 실시간으로 변하는 데이터를 클라이언트에게 업데이트합니다.
