package org.bonede.cafebit;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class Bencode {
    public static class BError extends RuntimeException{
        public BError(String msg){
            super(msg);
        }
    }
    public final static int MAX_STACK = 1024;
    public final static int BUFFER_SIZE = 1024 * 1024;
    enum ParseState{
        StateStart,
        StateEnd,
        StateE,
        StateCol,
        StateIntStart,
        StateInt,
        StateNegate,
        StateBytesSizeStart,
        StateBytesSize,

        StateBytes,
        StateList,
        StateListStart,
        StateDict,
        StateDictStart,
    }
    public static class BPair{
        public byte[] key;
        public BValue value;
    }
    public enum BValueType{
        BInt,
        BBytes,
        BList,
        BDict
    }
    public static class BValue{
        public BValueType type;
        public long intValue;
        public ByteBuffer bytes;
        public ArrayList<BValue> list;
        public ArrayList<BPair> dict;
        public static BValue intValue(){
            BValue bValue = new BValue();
            bValue.type = BValueType.BInt;
            return bValue;
        }

        public static BValue bytesValue(){
            BValue bValue = new BValue();
            bValue.type = BValueType.BBytes;
            return bValue;
        }

        public static BValue listValue(){
            BValue bValue = new BValue();
            bValue.type = BValueType.BList;
            bValue.list = new ArrayList<>();
            return bValue;
        }

        public static BValue dictValue(){
            BValue bValue = new BValue();
            bValue.type = BValueType.BDict;
            bValue.dict = new ArrayList<>();
            return bValue;
        }
    }
    public static class Parser{
        public ParseState state;
        public byte[] src;
        public int pos;
        public BValue bValue;
        public BValue[] stack;
        public int sp;
        public ByteBuffer buffer;
        public Parser(byte[] src){
            this.state = ParseState.StateStart;
            this.src = src;
            this.pos = 0;
            this.stack = new BValue[MAX_STACK];
            this.sp = 0;
            this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        }

        public void push(BValue bValue){
            if(this.sp == MAX_STACK){
                throw new BError("Nest too deep");
            }
            this.stack[this.sp++] = bValue;
        }

        public BValue pop(){
            BValue bValue = this.stack[--this.sp];
            this.bValue = bValue;
            return bValue;
        }

        public void startBuf(){
            this.buffer.rewind();
        }
        public void writeBuff(byte b){
            this.buffer.put(b);
        }

        public byte nextByte(){
            if(this.src.length == 0 || this.pos == this.src.length){
                this.pos++;
                return 0;
            }
            return this.src[this.pos++];
        }

        public static boolean isDigit(byte b)  {
            return b >= '0' && b <= '9';
        }


        private void setState(ParseState state, byte b){
            this.state = state;
            if(state == ParseState.StateIntStart){
                popBytes();
                this.push(BValue.intValue());
            }if(state == ParseState.StateListStart){
                popBytes();
            }if(state == ParseState.StateDictStart){
                popBytes();
            }if(state == ParseState.StateInt){
                this.writeBuff(b);
            }else if(state == ParseState.StateBytesSizeStart){;
                popBytes();
                this.startBuf();
                this.writeBuff(b);
                this.push(BValue.bytesValue());
            }else if(state == ParseState.StateBytesSize){
                this.writeBuff(b);
            }else if(state == ParseState.StateBytes){
                this.writeByte(b);
            }else if(state == ParseState.StateE){
                BValue value = peak();
                if(value == null){
                    throw new BError("invalid input");
                }
                switch (value.type){
                    case BInt: {
                        value.intValue = intBufferValue();
                        pop();
                        break;
                    }
                }
            }else if(state == ParseState.StateEnd){
                popBytes();
            }else if(state == ParseState.StateCol){
                BValue value = this.peak();
                if(value.type == BValueType.BBytes){
                    value.bytes = ByteBuffer.allocate(this.intBufferValue());
                }
            }
        }

        public void popBytes(){
            BValue value = peak();
            if(value != null && value.type == BValueType.BBytes){
                pop();
            }
        }

        public void stateStart(byte b){
            if(b == 'i'){
                setState(ParseState.StateIntStart, b);
            }else if(isDigit(b)){
                setState(ParseState.StateBytesSizeStart, b);
            }else if(b == 'l'){
                setState(ParseState.StateListStart, b);
            }else if(b == 'd'){
                setState(ParseState.StateDictStart, b);
            }else{
                throw unexpectError(b);
            }
        }

        private BValue peak(){
            if(this.sp >= 1){
                return this.stack[this.sp - 1];
            }else{
                return null;
            }
        }

        private int writeByte(byte b){
            ensureBytesCapacity();
            this.peak().bytes.put(b);
            return this.peak().bytes.remaining();
        }
        private boolean bytesHasRemaining(){
            return this.peak().bytes.remaining() > 0;
        }
        private void ensureBytesCapacity(){
            if(!bytesHasRemaining()){
                throw new BError("Bytes exceeded limit");
            }
        }
        private void stateCol(byte b) {
            BValue bvalue = this.peak();
            if(bvalue == null){
                throw unexpectError(b);
            }

            switch (bvalue.type){
                case BInt: {
                    if(b == '-'){
                        this.state = ParseState.StateNegate;
                        this.writeBuff(b);
                        break;
                    }else if(isDigit(b)){
                        this.state = ParseState.StateInt;
                        this.writeBuff(b);
                        break;
                    }else{
                        throw unexpectError(b);
                    }
                }
                case BBytes: {
                    if(bytesHasRemaining()){
                        setState(ParseState.StateBytes, b);
                    }else{
                        if(b == 0){
                            setState(ParseState.StateE, b);
                        }else{
                            throw new BError("Exceeded limit");
                        }
                    }
                    break;
                }
                case BDict: {
                    this.state = ParseState.StateDict;
                    break;
                }
                case BList: {
                    this.state = ParseState.StateList;
                    break;
                }
            }
        }
        private void stateE(byte b) {
            BValue bValue = this.peak();
            if(bValue == null){
                if(b == 0){
                    setState(ParseState.StateEnd, b);
                }else{
                    throw unexpectError(b);
                }
            }else{
                switch (bValue.type){
                    case BList: {
                        if(b == 'i'){
                            setState(ParseState.StateIntStart, b);
                            break;
                        }else if(isDigit(b)){
                            setState(ParseState.StateBytesSizeStart, b);
                            break;
                        }else if(b == 'd'){
                            setState(ParseState.StateDictStart, b);
                            break;
                        }else if(b == 'l'){
                            setState(ParseState.StateListStart, b);
                            break;
                        }else{
                            throw unexpectError(b);
                        }
                    }
                    case BDict: {
                        this.state = ParseState.StateDict;
                        break;
                    }

                }
            }


        }

        private void stateDict(byte b) {

        }

        private void stateDictStart(byte b) {

        }

        private void stateList(byte b) {

        }

        private void stateListStart(byte b) {

        }

        private void stateBytes(byte b) {
            BValue bValue = this.peak();
            if(bValue.bytes.remaining() == 0){
                if(b == 0){
                    setState(ParseState.StateEnd, b);
                }else if(b == 'i'){
                    setState(ParseState.StateIntStart, b);
                }else if(b == 'd'){
                    setState(ParseState.StateDictStart, b);
                }else if(b == 'l'){
                    setState(ParseState.StateListStart, b);
                }else if(isDigit(b)){
                    setState(ParseState.StateBytes, b);
                }
            }else{
                writeByte(b);
            }
        }

        private void stateBytesSizeStart(byte b) {
            if(isDigit(b)){
                setState(ParseState.StateBytesSize, b);
            }else if(b == ':'){
                setState(ParseState.StateCol, b);
            }else{
                throw unexpectError(b);
            }
        }

        private void stateBytesSize(byte b) {
            if(isDigit(b)){
                setState(ParseState.StateBytesSize, b);
            }else if(b == ':'){
                setState(ParseState.StateCol, b);
            }else{
                throw unexpectError(b);
            }
        }

        private void stateBytesStart(byte b) {

        }

        private void stateInt(byte b) {
            if(isDigit(b)){
                setState(ParseState.StateInt, b);
            }else if(b == 'e'){
                setState(ParseState.StateE, b);
            }else{
                throw unexpectError(b);
            }
        }

        private long longBufferValue() {
            return Long.parseLong(new String(buffer.array(), 0, buffer.position()));
        }
        private int intBufferValue() {
            return Integer.parseInt(new String(buffer.array(), 0, buffer.position()));
        }

        private void stateNegate(byte b) {
            if(isDigit(b)){
                this.state = ParseState.StateInt;
                this.writeBuff(b);
            }else{
                throw unexpectError(b);
            }
        }

        private void stateIntStart(byte b) {
            if(b == ':'){
                this.state = ParseState.StateCol;
            }else{
                throw unexpectError(b);
            }
        }

        private BError unexpectError(byte b){
            String value = b == 0 ? "<EOF>" : new String(new byte[]{b});
            return new BError(String.format("Unexpected: `%s`", value));
        }

        public int tokenLength(){
            return src.length + 1;
        }


        private void send(byte b){
            switch (this.state) {
                case StateStart: this.stateStart(b); break;
                case StateCol: this.stateCol(b); break;
                case StateNegate: this.stateNegate(b); break;
                case StateIntStart: this.stateIntStart(b); break;
                case StateInt: this.stateInt(b); break;
                case StateBytesSizeStart: this.stateBytesSizeStart(b); break;
                case StateBytesSize: this.stateBytesSize(b); break;
                case StateBytes: this.stateBytes(b); break;
                case StateListStart: this.stateListStart(b); break;
                case StateList: this.stateList(b); break;
                case StateDictStart: this.stateDictStart(b); break;
                case StateDict: this.stateDict(b); break;
                case StateE: this.stateE(b); break;
                default: throw new BError("Invalid state " + this.state);
            }
        }

        public BValue parse(){
            byte b;
            while(this.pos <= this.src.length){
                b = this.nextByte();
                send(b);
            }
            if(this.state == ParseState.StateEnd && this.sp == 0){
                return this.bValue;
            }
            throw new BError("Invalid input");
        }



    }

    public static BValue parse(String str){
        Bencode.Parser parser = new Bencode.Parser(str.getBytes());
        parser.parse();
        return parser.bValue;
    }
}
