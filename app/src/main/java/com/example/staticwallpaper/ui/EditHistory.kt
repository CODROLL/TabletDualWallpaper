package com.example.staticwallpaper.ui

class EditHistory<T>(initial:T,private val maxSize:Int=50){
    var current:T=initial;private set
    private val undo=ArrayDeque<T>();private val redo=ArrayDeque<T>()
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    fun commit(value:T){if(value==current)return;if(undo.size==maxSize)undo.removeFirst();undo.addLast(current);current=value;redo.clear()}
    fun undo():T{if(undo.isNotEmpty()){redo.addLast(current);current=undo.removeLast()};return current}
    fun redo():T{if(redo.isNotEmpty()){undo.addLast(current);current=redo.removeLast()};return current}
}
