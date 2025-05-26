package cn.study.compilerclass.assembly;

import cn.study.compilerclass.model.ConstTableEntry;
import cn.study.compilerclass.model.MiddleTableEntry;
import cn.study.compilerclass.model.VariableTableEntry;
import java.util.ArrayList;

public class AssemblyGenerator {

  private final ArrayList<ConstTableEntry> constTable;
  private final ArrayList<VariableTableEntry> variableTable;
  private final ArrayList<MiddleTableEntry> middleCode;
  private final StringBuilder asmCode;

  public AssemblyGenerator(ArrayList<ConstTableEntry> constTable, ArrayList<VariableTableEntry> variableTable, ArrayList<MiddleTableEntry> middleCode) {
    this.constTable = constTable;
    this.variableTable = variableTable;
    this.middleCode = middleCode;
    this.asmCode = new StringBuilder();
  }

  public String generateAssembly() {

    // 固定模板 1
    fixed1();
    // 变量
    vars();
    // 固定模板 2
    fixed2();
    // 四元式
    middles();
    // 固定模板 3
    fixed3();

    return asmCode.toString();
  }

  private void fixed1() {
    asmCode.append("""
        assume cs:code,ds:data,ss:stack,es:extended
        
        extended segment
        db 1024 dup (0)
        extended ends
        
        stack segment
        db 1024 dup (0)
        stack ends
        
        dispmsg macro message
        lea dx, message
        mov ah, 9
        int 21h
        endm
        
        """);
  }

  private void vars() {
    asmCode.append("""
        data segment
        _buff_p db 256 dup (24h)
        _buff_s db 256 dup (0)
        _msg_p db 0ah,'Output:',0
        _msg_s db 0ah,'Input:',0
        next_row db 0dh,0ah,'$'
        error db 'input error, please re-enter: ','$'
        
        """);
    for (VariableTableEntry entry : variableTable) {
      String name = entry.getName();
      asmCode.append(String.format("_%s dw 0\n", name));
    }
    asmCode.append("data ends\n\n");
  }

  private void fixed2() {
    asmCode.append("""
              code segment
              start: mov ax,extended
              mov es,ax
              mov ax,stack
              mov ss,ax
              mov sp,1024
              mov bp,sp
              mov ax,data
              mov ds,ax
        
        """);
  }

  private void middles() {
    for (MiddleTableEntry entry : middleCode) {
      int id = entry.getId();
      String op = entry.getOp();
      String arg1 = format(entry.getArg1());
      String arg2 = format(entry.getArg2());
      String result = format(entry.getResult());
      if (op.equals("quit")) {
        asmCode.append("quit: mov ah, 4ch\n");
        asmCode.append("int 21h\n");
      } else if (!op.equals("main")) {
        asmCode.append(String.format("_%d: ", id));
        switch (op) {
          case "=" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "call" -> {
            asmCode.append(String.format("call %s\n", arg1));
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "+" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("add ax, %s\n", arg2));
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "-" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("sub ax, %s\n", arg2));
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "*" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("mov bl, %s\n", arg2));
            asmCode.append("imul bl\n");
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "/" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("mov bl, %s\n", arg2));
            asmCode.append("idiv bl\n");
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "%" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("mov bl, %s\n", arg2));
            asmCode.append("idiv bl\n");
            asmCode.append(String.format("mov %s, dx\n", result));
          }
          case "para" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append("push ax\n");
          }
        }
      }
    }
  }

  private void fixed3() {
    asmCode.append("""
        read proc near
        push bp
        mov bp, sp
        mov bx,offset _msg_s
        call _print
        push bx
        push cx
        push dx
        proc_pre_start:
        xor ax, ax
        xor bx, bx
        xor cx, cx
        xor dx, dx
        proc_judge_sign:
        mov ah, 1
        int 21h
        cmp al, '-'
        jne proc_next
        mov dx, 0ffffh
        jmp proc_digit_in
        proc_next:
        cmp al, 30h
        jb proc_unexpected
        cmp al, 39h
        ja proc_unexpected
        sub al, 30h
        shl bx, 1
        mov cx, bx
        shl bx, 1
        shl bx, 1
        
        add bx, cx
        add bl, al
        adc bh, 0
        proc_digit_in:
        mov ah, 1
        int 21h
        jmp proc_next
        
        proc_save:
        cmp dx, 0ffffh
        jne proc_result_save
        neg bx
        proc_result_save:
        mov ax, bx
        jmp proc_input_done
        
        proc_unexpected:
        cmp al, 0dh
        je proc_save
        dispmsg next_row
        dispmsg error
        jmp proc_pre_start
        
        proc_input_done:
        pop dx
        pop cx
        pop bx
        pop bp
        ret
        read endp
        write proc near
        push bp
        mov bp, sp
        push ax
        push bx
        push cx
        push dx
        mov bx,offset _msg_p
        call _print
        xor cx, cx
        mov bx, [bp+4]
        test bx, 8000h
        jz proc_nonneg
        neg bx
        mov dl,'-'
        mov ah, 2
        int 21h
        proc_nonneg:
        mov ax, bx
        cwd
        mov bx, 10
        proc_div_again:
        xor dx, dx
        div bx
        add dl, 30h
        push dX
        inc cx
        cmp ax, 0
        jne proc_div_again
        proc_digit_out:
        pop dx
        mov ah, 2
        int 21h
        loop proc_digit_out
        proc_output_done:
        pop dx
        pop cx
        pop bx
        pop ax
        pop bp
        ret 2
        write endp
        
        _print: mov si,0
        mov di,offset _buff_p
        _p_lp_1: mov al,ds:[bx+si]
        cmp al,0
        je _p_brk_1
        mov ds:[di],al
        inc si
        inc di
        jmp short _p_lp_1
        _p_brk_1: mov dx,offset _buff_p
        mov ah,09h
        int 21h
        mov cx,si
        mov di,offset _buff_p
        _p_lp_2: mov al,24h
        mov ds:[di],al
        inc di
        loop _p_lp_2
        ret
        code ends
        end start""");
  }

  private String format(String str) {
    // 如果是变量名，查找变量表
    for (VariableTableEntry entry : variableTable) {
      if (entry.getName().equals(str)) {
        return String.format("ds:[_%s]", str);
      }
    }
    // 如果是常量名，查找常量表
    // for (ConstTableEntry entry : constTable) {
    //   if (entry.getName().equals(str)) {
    //     return String.format("ds:[_%s]", str);
    //   }
    // }
    // 如果是临时变量名，直接返回
    if (str.startsWith("$_t")) {
      return String.format("es:[%s]", str.substring(3));
    }
    // 其他情况直接返回
    return str;
  }
}
