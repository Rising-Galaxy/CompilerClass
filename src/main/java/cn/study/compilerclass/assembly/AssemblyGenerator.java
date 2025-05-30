package cn.study.compilerclass.assembly;

import cn.study.compilerclass.model.ConstTableEntry;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.MiddleTableEntry;
import cn.study.compilerclass.model.VariableTableEntry;
import cn.study.compilerclass.utils.OutInfo;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssemblyGenerator {

  private final String src = "汇编代码";
  private final ArrayList<ConstTableEntry> constTable;
  private final ArrayList<VariableTableEntry> variableTable;
  private final ArrayList<FunctionTableEntry> functionTable;
  private final ArrayList<MiddleTableEntry> middleCode;
  private final StringBuilder asmCode;
  private final OutInfo outInfos;

  /**
   * 构造函数
   *
   * @param constTable    常量表
   * @param variableTable 变量表
   * @param functionTable 函数表
   * @param middleCode    中间代码
   */
  public AssemblyGenerator(ArrayList<ConstTableEntry> constTable, ArrayList<VariableTableEntry> variableTable, ArrayList<FunctionTableEntry> functionTable, ArrayList<MiddleTableEntry> middleCode, OutInfo outInfos) {
    this.constTable = constTable;
    this.variableTable = variableTable;
    this.functionTable = functionTable;
    this.middleCode = middleCode;
    this.asmCode = new StringBuilder();
    this.outInfos = outInfos;
  }

  /**
   * 生成 8086 汇编代码<p/>
   * <p>
   * 处理顺序: <br/> 1. {@link AssemblyGenerator#fixed1() 固定模板 1}<br/> 2. {@link AssemblyGenerator#vars() 变量}<br/> 3.
   * {@link AssemblyGenerator#fixed2() 固定模板 2}<br/> 4. {@link AssemblyGenerator#middles() 四元式}<br/> 5.
   * {@link AssemblyGenerator#fixed3() 固定模板 3}<br/>
   *
   * @return 生成的汇编代码字符串
   */
  public String generateAssembly() {

    try {
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
    } catch (myException e) {
      return null;
    }
  }

  /**
   * 固定模板 1<br/>包含数据段、代码段和栈段的初始化
   */
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

  /**
   * 生成变量的汇编代码<br/>包括数据段的定义和变量的初始化
   *
   * @throws myException 如果遇到未支持的类型，抛出自定义异常
   */
  private void vars() throws myException {
    asmCode.append("""
        data segment
        _buff_p db 256 dup (24h)
        _buff_s db 256 dup (0)
        _msg_s db '> ','$'
        _true_msg db 'True','$'
        _false_msg db 'False','$'
        next_row db 0dh,0ah,'$'
        error db 'input error, please re-enter',0dh,0ah,'> ','$'
        """);
    for (VariableTableEntry entry : variableTable) {
      // 变量名
      String name = entry.getName();
      asmCode.append("_").append(name).append(" ");
      // 变量类型
      String type = entry.getType();
      String assemblyType = typeToAssembly(type);
      asmCode.append(assemblyType).append(" ");
      // 默认值
      asmCode.append(getValue(entry)).append("\n");
    }
    asmCode.append("data ends\n\n");
  }

  /**
   * 固定模板 2<br/>包含数据段、代码段和栈段的初始化
   */
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

  /**
   * 生成四元式对应的汇编代码<br/>根据中间代码生成对应的汇编代码
   *
   * @throws myException 如果遇到未支持的操作符或类型，抛出自定义异常
   */
  private void middles() throws myException {
    for (MiddleTableEntry entry : middleCode) {
      int id = entry.getId();
      String op = entry.getOp();
      String arg1 = format(entry.getArg1());
      String arg2 = format(entry.getArg2());
      String result = format(entry.getResult());
      if (!op.equals("main")) {
        asmCode.append(String.format("_%d: ", id));
        switch (op) {
          case "=" -> {
            asmCode.append(String.format("mov ax, %s\n", arg1));
            asmCode.append(String.format("mov %s, ax\n", result));
          }
          case "call" -> {
            switch (arg1) {
              case "input", "put", "putc", "putb" -> {
                asmCode.append(String.format("call %s\n", arg1));
                asmCode.append(String.format("mov %s, ax\n", result));
              }
              default -> // 跳过不支持的函数调用
                  error("暂未支持自定义函数调用: " + arg1);
            }
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
          case "j" -> asmCode.append(String.format("jmp far ptr _%s\n", result));
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
          case "quit" -> {
            asmCode.append("nop\n"); // 占位符
            asmCode.append("quit: mov ah, 4ch\n");
            asmCode.append("int 21h\n");
          }
          default -> error(String.format("无效的操作符 '%s'", op));
        }
      }
    }
  }

  /**
   * 固定模板 3<br/>包含输入输出函数 read 和 write
   */
  private void fixed3() {
    asmCode.append("""
        ; 输入函数
        input proc near
        push bp
        mov bp, sp
        mov bx, offset _msg_s
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
        input endp
        
        ; 输出函数 put(int)
        put proc near
        push bp
        mov bp, sp
        push ax
        push bx
        push cx
        push dx
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
        put endp
        
        ; 输出函数 putb(bool)
        putb proc near
        push bp
        mov bp, sp
        push ax
        push bx
        push dx
        mov bx, [bp+4]
        cmp bx, 0
        jnz is_true
        dispmsg _false_msg
        jmp bool_done
        is_true:
        dispmsg _true_msg
        bool_done:
        pop dx
        pop bx
        pop ax
        pop bp
        ret 2
        putb endp
        
        ; 输出函数 putc(char)
        putc proc near
        push bp
        mov bp, sp
        push ax
        push bx
        push cx
        push dx
        mov dx, [bp+4] ; 获取参数
        mov ah, 02h ; DOS显示字符功能
        int 21h
        pop dx
        pop cx
        pop bx
        pop ax
        pop bp
        ret 2
        putc endp
        
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

  /**
   * 格式化变量名、常量名和临时变量名为汇编语言中的表示形式
   *
   * @param str 需要格式化的字符串
   * @return 格式化后的字符串
   * @throws myException 如果遇到未支持的类型，抛出自定义异常
   */
  private String format(String str) throws myException {
    // 如果是变量名，查找变量表
    for (VariableTableEntry entry : variableTable) {
      if (entry.getName().equals(str)) {
        return String.format("ds:[_%s]", str);
      }
    }
    // 如果是常量名，查找常量表
    for (ConstTableEntry entry : constTable) {
      if (entry.getName().equals(str)) {
        return String.format("%s", getValue(entry));
      }
    }
    // 如果是临时变量名，直接返回
    if (str.startsWith("$_t")) {
      return String.format("es:[%s]", Integer.parseInt(str.substring(3)) * 2);
    }

    if (str.equals("True") || str.equals("False")) {
      // 布尔值直接转换为 1 或 0
      return str.equals("True") ? "1" : "0";
    }

    // 字符
    if (str.startsWith("'") && str.endsWith("'")) {
      // 如果是字符，进行转义
      String charValue = str.substring(1, str.length() - 1);
      switch (charValue) {
        case "\\n" -> {
          return "0Ah"; // 换行符
        }
        case "\\t" -> {
          return "09h"; // 制表符
        }
        case "\\r" -> {
          return "0Dh"; // 回车符
        }
        case "\\0" -> {
          return "00h"; // 空字符
        }
        case "\\'" -> {
          return "27h"; // 单引号
        }
        case "\\\\" -> {
          return "5Ch"; // 反斜杠
        }
        default -> {
          // 其他字符直接返回其 ASCII 值
          return String.format("0%02Xh", (int) charValue.charAt(0));
        }
      }
    }
    return str;
  }

  /**
   * 获取变量/常量的值并格式化为汇编语言中的表示形式 同时也处理了默认值
   *
   * @param entry 变量/常量表条目
   * @return 格式化后的值
   * @throws myException 如果遇到未支持的类型，抛出自定义异常
   */
  private String getValue(ConstTableEntry entry) throws myException {
    // 如果是布尔类型，返回 1 或 0
    if (entry.getType().equals("bool")) {
      return entry.getValue().equals("True") ? "1" : "0";
    }
    // 如果为空
    if (entry.getValue().equals("null")) {
      switch (entry.getType()) {
        case "int" -> {
          return "0";
        }
        case "char" -> {
          return "' '";
        }
        case "bool" -> {
          return "0"; // 布尔类型默认为 false
        }
        default -> {
          error(String.format("生成四元式时遇到了暂未支持的类型: %s", entry.getType()));
          return "null"; // 未知类型返回 null
        }
      }
    }
    // 否则直接返回数值
    return entry.getValue();
  }

  /**
   * 将变量类型转换为汇编语言中的数据类型
   *
   * @param type 变量类型
   * @return 汇编语言中的数据类型
   * @throws myException 如果遇到未支持的类型，抛出自定义异常
   */
  private String typeToAssembly(String type) throws myException {
    return switch (type) {
      case "int", "char", "bool" -> "dw";
      default -> {
        error(String.format("生成四元式时遇到了暂未支持的类型: %s", type));
        yield ""; // 不会执行到这里
      }
    };
  }

  /**
   * 记录错误信息并抛出异常
   *
   * @param message 错误信息
   * @throws myException 自定义异常
   */
  private void error(String message) throws myException {
    outInfos.error(src, message);
    throw new myException(message);
  }

  /**
   * 自定义异常类
   */
  private static class myException extends Exception {

    public myException(String message) {
      super(message);
    }
  }
}