package org.bonede.cafebit;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bonede.cafebit.Bencode.BValueTag.*;
import static org.bonede.cafebit.Bencode.Parser.Context.*;
import static org.bonede.cafebit.Bencode.Parser.State.*;

public class Bencode {

    public static class BError extends RuntimeException{
        public BError(String msg){
            super(msg);
        }
    }

    public final static int BUFFER_SIZE = 1024 * 1024;
    public static class BPair{
        private BValue key;
        private BValue value;

        public BValue getKey() {
            return key;
        }

        public BValue getValue() {
            return value;
        }
    }

    public enum BValueTag{
        Int,
        Bytes,
        List,
        Dict
    }

    public final static int STACK_SIZE = 1024;
    public static class BValue{
        private int sp;
        private BValue[] stack;
        private BValueTag tag;
        private int intValue;
        private byte[] bytesValue;
        public void encode(OutputStream outputStream) throws IOException {

            switch (tag){
                case Int: {
                    outputStream.write('i');
                    outputStream.write(String.valueOf(intValue).getBytes());
                    outputStream.write('e');
                } break;
                case Bytes: {
                    outputStream.write(String.valueOf(bytesValue.length).getBytes());
                    outputStream.write(':');
                    outputStream.write(bytesValue);
                    break;
                }case Dict: {
                    outputStream.write('d');
                    for(BPair pair : dictValue){
                        pair.key.encode(outputStream);
                        pair.value.encode(outputStream);
                    }
                    outputStream.write('e');
                    break;
                }case List: {
                    outputStream.write('l');
                    for(BValue value : listValue){
                        value.encode(outputStream);
                    }
                    outputStream.write('e');
                    break;
                }
            }
        }
        public BValueTag getTag(){
            return tag;
        }

        public BValue get(String key){
            byte[] bytes = key.getBytes();
            for(BPair pair : dictValue){
                if(Arrays.equals(pair.key.bytesValue, bytes)){
                    return pair.value;
                }
            }
            return null;
        }


        public int getIntValue() {
            return intValue;
        }

        public byte[] getBytesValue() {
            return bytesValue;
        }

        public ArrayList<BValue> getListValue() {
            return listValue;
        }

        public ArrayList<BPair> getDictValue() {
            return dictValue;
        }

        private ArrayList<BValue> listValue;
        private ArrayList<BPair> dictValue;
        private void push(BValue bValue){
            this.stack[sp++] = bValue;
        }
        private void pop(){
            this.sp--;
        }
        private void addListItem(BValue bValue){
            this.top().listValue.add(bValue);
            push(bValue);
        }
        private void addDictItem(){
            BValue key = BValue.bytesValue();
            BPair pair = new BPair();
            pair.key = key;
            this.top().dictValue.add(pair);
            push(key);
        }

        private void addDictValue(BValue bValue){
            this.top().dictValue.get(this.top().dictValue.size() - 1).value = bValue;
            push(bValue);
        }


        public BValue(){
            this.stack = new BValue[STACK_SIZE];
            push(this);
        }

        public BValue top(){
            return this.stack[sp-1];
        }

        public static BValue intValue(){
            BValue value = new BValue();
            value.tag = Int;
            return value;
        }

        public static BValue bytesValue(){
            BValue value = new BValue();
            value.tag = Bytes;
            return value;
        }

        public static BValue listValue(){
            BValue value = new BValue();
            value.tag = List;
            value.listValue = new ArrayList<>();
            return value;
        }

        public static BValue dictValue(){
            BValue value = new BValue();
            value.tag = Dict;
            value.dictValue = new ArrayList<>();
            return value;
        }

        @Override
        public String toString(){
            switch (tag){
                case Int: return String.valueOf(intValue);
                case List: return listValue.stream().map(i -> i.toString()).collect(Collectors.joining(", "));
                case Dict: return dictValue.stream().map(p -> p.key + ": " + p.value).collect(Collectors.joining(", "));
                case Bytes: return new String(bytesValue);
                default: throw new RuntimeException("Invalid tag: " + tag);
            }
        }

    }



    public static class Parser{
        private byte[] src;
        private static int BUFFER_SIZE = 1024 * 1024;
        private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        enum Context{
            ContextRoot,
            ContextList,
            ContextInt,
            ContextBytes,
            ContextDict,
            ContextDictKey,
            ContextDictValue,
        }
        enum State{
            StateStart,
            StateEnd,
            StateE,
            StateColon,
            StateI,
            StateNegate,
            StateDigit,
            StateBytes,
            StateL,
            StateD
        }
        private State state;


        private Context[] stack;

        private int sp;

        public Context context(){
            if(sp == 0){
                return null;
            }else{
                return stack[sp - 1];
            }
        }

        private void push(Context context){
            stack[sp++] = context;
        }

        private int vsp;

        public Parser(String src){
            this.src = src.getBytes();
            init();
        }

        private void init(){
            this.stack = new Context[STACK_SIZE];
            this.sp = 0;
            this.vsp = 0;
            this.state = StateStart;
            push(ContextRoot);
        }

        public Parser(byte[] src){
            this.src = src;
            init();
        }

        private boolean isI(byte event){
            return event == 'i';
        }

        private boolean isDigit(byte event){
            return event >= '0' && event <= '9';
        }

        private boolean isL(byte event){
            return event == 'l';
        }

        private boolean isE(byte event){
            return event == 'e';
        }

        private boolean isD(byte event){
            return event == 'd';
        }

        private int readBufferAsInt(){
            byte[] bytes = new byte[buffer.position()];
            readBuffer(bytes);
            this.buffer.limit(BUFFER_SIZE);
            return Integer.parseInt(new String(bytes));
        }

        private void readBuffer(byte[] bytes) {
            buffer.flip();
            buffer.get(bytes);
            buffer.rewind();
        }


        public void setState(State state, byte event){
            this.state = state;
        }

        private void pop(){
            this.sp--;
        }

        private BError unexpected(byte event, String... expected){
            String expectedMsg = expected.length > 0 ?
                     Stream.of(expected).collect(Collectors.joining(", "))
                    : "";
            String value = event == 0 ? "<EOF>" : new String(new byte[]{event});
            return new BError(String.format("Unexpect: %s, expecting: %s", value, expectedMsg));
        }

        private BValue value;

        private void stateStart(byte event){
            if(isI(event)){
                push(ContextInt);
                this.value = BValue.intValue();
                setState(StateI, event);
            }else if(isL(event)){
                push(ContextList);
                this.value = BValue.listValue();
                setState(StateL, event);
            }else if(isD(event)){
                this.value = BValue.dictValue();
                push(ContextDict);
                setState(StateD, event);
            }else if(isDigit(event)){
                this.value = BValue.bytesValue();
                writeBuffer(event);
                push(ContextBytes);
                setState(StateDigit, event);
            }else{
                throw unexpected(event, "i", "l", "d", "<digit>");
            }
        }

        void writeBuffer(byte event){
            this.buffer.put(event);
        }

        public boolean isColon(byte event){
            return event == ':';
        }

        private void stateDigit(byte event){
            Context context = context();
            if(context == ContextInt){
                if(isDigit(event)){
                    writeBuffer(event);
                    setState(StateDigit, event);
                }else if(isE(event)){
                    pop();
                    setState(StateE, event);
                    // TODO handleValue
                    value.top().intValue = readBufferAsInt();
                    value.pop();
                }else{
                    throw unexpected(event, "e", "<digit>");
                }
            }else if(context == ContextBytes){
                if(isDigit(event)){
                    writeBuffer(event);
                    setState(StateDigit, event);
                }else if(isColon(event)){
                    setState(StateColon, event);
                }else{
                    throw unexpected(event, ":", "<digit>");
                }
            }else {
                throw invalidContext(context);
            }

        }

        private void stateNegate(byte event){
            Context context = context();
            if(context == ContextInt){
                if(isDigit(event)){
                    writeBuffer(event);
                    setState(StateDigit, event);
                }else{
                    throw unexpected(event, "<digit>");
                }
            }else {
                throw invalidContext(context);
            }

        }

        private boolean isEOF(byte event){
            return event == 0;
        }
        public BValue getValue(){
            return value;
        }
        private void stateE(byte event){
            Context context = context();
            if(context == ContextRoot){
                if(isEOF(event)){
                    setState(StateEnd, event);
                }else{
                    throw unexpected(event, "<EOF>");
                }
            }else if(context == ContextList){
                if(isE(event)){
                    pop();
                    setState(StateE, event);
                }else if(isI(event)){
                    push(ContextInt);
                    value.addListItem(BValue.intValue());
                    setState(StateI, event);
                }else if(isL(event)){
                    push(ContextList);
                    value.addListItem(BValue.listValue());
                    setState(StateL, event);
                }else if(isD(event)){
                    push(ContextDict);
                    value.addListItem(BValue.dictValue());
                    setState(StateD, event);
                }else if(isDigit(event)){
                    writeBuffer(event);
                    push(ContextBytes);
                    value.addListItem(BValue.bytesValue());
                    setState(StateDigit, event);
                }else{
                    throw unexpected(event, "e", "i", "d", "l", "<digits>");
                }
            }else if(context == ContextDict){
                if(isEOF(event)){
                    pop();
                    setState(StateEnd, event);
                }else{
                    throw unexpected(event, "<EOF>");
                }
            }else if(context == ContextDictValue){
                if(isE(event)){
                    pop();
                    setState(StateE, event);
                }else if(isDigit(event)){
                    push(ContextDictKey);
                    push(ContextBytes);
                    value.addDictItem();
                    writeBuffer(event);
                    setState(StateDigit, event);
                }else if(isEOF(event)){
                    pop();
                    setState(StateEnd, event);
                }else{
                    throw unexpected(event, "e", "i", "d", "l", "<digits>");
                }
            }

        }

        private boolean isNegate(byte event){
            return event == '-';
        }


        private BError invalidContext(Context context){
            return new BError("Invalid context: " + context);
        }

        private void stateI(byte event){
            Context top = context();
            if(top == ContextInt){
                if(isNegate(event)){
                    writeBuffer(event);
                    setState(StateNegate, event);
                }else if(isDigit(event)){
                    writeBuffer(event);
                    setState(StateDigit, event);
                }else{
                    throw unexpected(event, "-", "<digit>");
                }
            }else{
                throw invalidContext(top);
            }

        }

        private void resetBufferLimit(int limit){
            this.buffer.limit(limit);
            this.buffer.position(0);
        }

        private void stateColon(byte event) {
            Context context = context();
            if(context == ContextBytes){
                int length = readBufferAsInt();
                value.top().bytesValue = new byte[length];
                resetBufferLimit(length);
                setState(StateBytes, event);
                writeBuffer(event);
            }else{
                throw invalidContext(context);
            }

        }

        private void stateBytes(byte event) {
            if(!buffer.hasRemaining()){
                pop();
                // TODO
                readBuffer(value.top().bytesValue);
                value.pop();
            }
            Context top = context();
            if(top == ContextRoot){
                if(isEOF(event)){
                    setState(StateEnd, event);
                }else {
                    throw new BError("Bytes exceeded limit: " + value.top().bytesValue.length);
                }
            }else if(top == ContextList){
                if(isI(event)){
                    push(ContextInt);
                    value.addListItem(BValue.intValue());
                    setState(StateI, event);
                }else if(isL(event)){
                    push(ContextList);
                    value.addListItem(BValue.listValue());
                    setState(StateL, event);
                }else if(isD(event)){
                    push(ContextDict);
                    value.addListItem(BValue.dictValue());
                    setState(StateD, event);
                }else if(isDigit(event)){
                    writeBuffer(event);
                    push(ContextBytes);
                    value.addListItem(BValue.bytesValue());
                    setState(StateDigit, event);
                }else if(isE(event)){
                    pop();
                    value.pop();
                    setState(StateE, event);
                }else{
                    throw unexpected(event, "e", "i", "l", "d", "<digit>");
                }
            }else if(top == ContextDictKey){
                if(isI(event)){
                    pop();
                    push(ContextDictValue);
                    push(ContextInt);
                    value.addDictValue(BValue.intValue());
                    setState(StateI, event);
                }else if(isL(event)){
                    pop();
                    push(ContextDictValue);
                    push(ContextList);
                    value.addDictValue(BValue.listValue());
                    setState(StateL, event);
                }else if(isD(event)){
                    pop();
                    push(ContextDictValue);
                    value.addDictValue(BValue.dictValue());
                    setState(StateD, event);
                }else if(isDigit(event)){
                    pop();
                    push(ContextDictValue);
                    push(ContextBytes);
                    writeBuffer(event);
                    value.addDictValue(BValue.bytesValue());
                    setState(StateDigit, event);
                }else{
                    throw unexpected(event, "i", "l", "d", "<digit>");
                }
            }else if(top == ContextDictValue){
                if(isE(event)){
                    pop();
                    setState(StateE, event);
                }else if(isDigit(event)){
                    pop();
                    push(ContextDictKey);
                    push(ContextBytes);
                    writeBuffer(event);
                    value.addDictItem();
                    setState(StateDigit, event);
                }else{
                    throw unexpected(event, "i", "l", "d", "<digit>");
                }
            }else {
                setState(StateBytes, event);
                writeBuffer(event);
            }
        }

        private void stateL(byte event) {
            if(isI(event)){
                push(ContextInt);
                value.addListItem(BValue.intValue());
                setState(StateI, event);
            }else if(isL(event)){
                push(ContextList);
                value.addListItem(BValue.listValue());
                setState(StateL, event);
            }else if(isD(event)){
                value.addListItem(BValue.dictValue());
                push(ContextDict);
                setState(StateD, event);
            }else if(isDigit(event)){
                value.addListItem(BValue.bytesValue());
                push(ContextBytes);
                setState(StateDigit, event);
                writeBuffer(event);
            }else if(isE(event)){
                pop();
                value.pop();
                setState(StateE, event);
            }else{
                throw unexpected(event, "i", "l", "d", "<digit>");
            }
        }

        private void stateD(byte event) {
            if(isDigit(event)){
                push(ContextDictKey);
                push(ContextBytes);
                writeBuffer(event);
                value.addDictItem();
                setState(StateDigit, event);
            }else if(isE(event)){
                pop();
                value.pop();
                setState(StateE, event);
            }else{
                throw unexpected(event, "<digit>");
            }
        }


        public void send(byte event){
            switch (state){
                case StateStart: stateStart(event); break;
                case StateI: stateI(event); break;
                case StateNegate: stateNegate(event); break;
                case StateDigit: stateDigit(event); break;
                case StateE: stateE(event); break;
                case StateColon: stateColon(event); break;
                case StateBytes: stateBytes(event); break;
                case StateL: stateL(event); break;
                case StateD: stateD(event); break;
                default: throw new BError("Invalid state: " + state);
            }
        }


        public boolean accepted(){
            return this.state == State.StateEnd;
        }

        public void parse(){
            for(byte event : this.src){
                this.send(event);
            }
            this.send((byte) 0);
        }

        public State getState(){
            return state;
        }
    }


    public static BValue parse(byte[] bytes){
        Bencode.Parser parser = new Bencode.Parser(bytes);
        parser.parse();
        if(!parser.accepted()){
            throw new BError("Invalid bencode input");
        }
        return parser.value;
    }




}
