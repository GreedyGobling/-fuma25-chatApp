package com.example.fuma25_chatapp

data class Message(var messageId: String = "", var sendorId: String = "", var name: String = "", var message: String = "", var time: Double){
    constructor():this("","","","",0.0)
}
// id for user, name for the ui part to show who sent message, message, and time for then the message was sent