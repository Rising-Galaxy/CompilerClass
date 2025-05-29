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

  // 目标机 8086
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
            if (arg1.equals("input")) {
              arg1 = "read";
            } else if (arg1.equals("output")) {
              arg1 = "write";
            }
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
            asmCode.append(String.format("mov bx, %s\n", arg2));
            asmCode.append("imul bx\n");
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "/" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append("cwd\n");  // 扩展AX到DX:AX用于有符号除法
            asmCode.append(String.format("mov bx, %s\n", arg2));
            asmCode.append("idiv bx\n");
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "%" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append("cwd\n");
            asmCode.append(String.format("mov bx, %s\n", arg2));
            asmCode.append("idiv bx\n");
            asmCode.append(String.format("mov %s, dx\n", result));  // 余数在DX中
          }
          case "para" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append("push ax\n");
          }
          case "j" -> {
            asmCode.append(String.format("jmp far ptr _%s\n", result));
          }
          case "jz" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1)); // 将 arg1 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("jne _ne_%d\n", id)); // 如果 AX 寄存器的值不为 0，跳转到 ne_id
            asmCode.append(String.format("jmp far ptr _%s\n", result)); // 否则跳转到 result
            asmCode.append(String.format("_ne_%d: nop\n", id)); // ne_id 处的空操作
          }
          case "jnz" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1)); // 将 arg1 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("je _ez_%d\n", id)); // 如果 AX 寄存器的值为 0，跳转到 ez_id
            asmCode.append(String.format("jmp far ptr _%s\n", result)); // 否则跳转到 result
            asmCode.append(String.format("_ez_%d: nop\n", id)); // ez_id 处的空操作
          }
          case ">" -> {
            asmCode.append("mov dx, 1\n"); // 默认结果为真
            asmCode.append(String.format("mov ax, %s\n", arg1)); // AX中存储的是arg1的值
            asmCode.append(String.format("cmp ax, %s\n", arg2)); // 比较arg1和arg2
            asmCode.append(String.format("jg _g_%d\n", id)); // 如果arg1大于arg2，跳转到_g_id
            asmCode.append("mov dx, 0\n"); // 如果不满足条件，将dx设为0
            asmCode.append(String.format("_g_%d: mov %s, dx\n", id, result)); // 将结果存储到result中
          }
          case "<" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("cmp ax, %s\n", arg2));
            asmCode.append(String.format("jl _l_%d\n", id));
            asmCode.append("mov dx, 0\n");
            asmCode.append(String.format("_l_%d: mov %s, dx\n", id, result));
          }
          case ">=" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("cmp ax, %s\n", arg2));
            asmCode.append(String.format("jge _ge_%d\n", id));
            asmCode.append("mov dx, 0\n");
            asmCode.append(String.format("_ge_%d: mov %s, dx\n", id, result));
          }
          case "<=" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("cmp ax, %s\n", arg2));
            asmCode.append(String.format("jle _le_%d\n", id));
            asmCode.append("mov dx, 0\n");
            asmCode.append(String.format("_le_%d: mov %s, dx\n", id, result));
          }
          case "==" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("cmp ax, %s\n", arg2));
            asmCode.append(String.format("je _ez_%d\n", id)); // 如果相等，跳转到 ez_id
            asmCode.append("mov dx, 0\n");
            asmCode.append(String.format("_ez_%d: mov %s, dx\n", id, result));
          }
          case "!=" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("cmp ax, %s\n", arg2));
            asmCode.append(String.format("jne _ne_%d\n", id)); // 如果不相等，跳转到 ne_id
            asmCode.append("mov dx, 0\n");
            asmCode.append(String.format("_ne_%d: mov %s, dx\n", id, result));
          }
          case "&&" -> {
            // && arg1 arg2 result
            asmCode.append("mov dx, 0\n"); // 默认结果为假
            asmCode.append(String.format("mov ax, %s\n", arg1)); // 将 arg1 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("je _and_false_%d\n", id)); // 如果 arg1 为假，跳转到 and_false_id
            asmCode.append(String.format("mov ax, %s\n", arg2)); // 将 arg2 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("je _and_false_%d\n", id)); // 如果 arg2 为假，跳转到 and_false_id
            asmCode.append("mov dx, 1\n"); // 如果 arg1 和 arg2 都为真，将 dx 设为 1
            asmCode.append(String.format("_and_false_%d: mov %s, dx\n", id, result)); // 将结果存储到 result 中
          }
          case "||" -> {
            // || arg1 arg2 result
            asmCode.append("mov dx, 1\n"); // 默认结果为真
            asmCode.append(String.format("mov ax, %s\n", arg1)); // 将 arg1 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("jne _or_true_%d\n", id)); // 如果 arg1 为真，跳转到 or_true_id
            asmCode.append(String.format("mov ax, %s\n", arg2)); // 将 arg2 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("jne _or_true_%d\n", id)); // 如果 arg2 为真，跳转到 or_true_id
            asmCode.append("mov dx, 0\n"); // 如果 arg1 和 arg2 都为假，将 dx 设为 0
            asmCode.append(String.format("_or_true_%d: mov %s, dx\n", id, result)); // 将结果存储到 result 中
          }
          case "!" -> {
            asmCode.append("mov dx, 1\n");
            asmCode.append(String.format("mov ax, %s\n", arg1)); // 将 arg1 的值移动到 AX 寄存器
            asmCode.append("cmp ax, 0\n"); // 比较 AX 寄存器的值是否为 0
            asmCode.append(String.format("je _not_true_%d\n", id)); // 如果 arg1 为假，跳转到 not_true_id
            asmCode.append("mov dx, 0\n"); // 如果 arg1 为真，将 dx 设为 0
            asmCode.append(String.format("_not_true_%d: mov %s, dx\n", id, result)); // 将结果存储到 result 中
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
      return String.format("es:[%s]", Integer.parseInt(str.substring(3)) * 2);
    }
    // Bool 值
    if (str.equals("True")) {
      return "1";
    } else if (str.equals("False")) {
      return "0";
    }
    return str;
  }
}
