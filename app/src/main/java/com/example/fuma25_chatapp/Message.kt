package com.example.fuma25_chatapp

data class Message(
    var messageId: String = "",
    var userId: String = "",
    var name: String = "",
    var text: String = "",
    var time: Double = 0.0){
    constructor():this("","","","",0.0)
}
// id for user, name for the ui part to show who sent message, message, and time for then the message was sent


/* firebase database structure
can be updated later

users/userId
    name: String
    email: String

chat-room/roomId
    users-list: String

chat-room/roomId/messages/messageId
    user: String
    text: String
    userId: String

*/
