/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2011-2016 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.cxx.parser;

import static org.sonar.sslr.tests.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import com.sonar.sslr.api.AstNode;

public class PreprocessorDirectivesTest extends ParserBaseTest {

  private String serialize(AstNode root) {
    List<String> values = new LinkedList<>();
    iterate(root, values);
    String s = String.join(" ", values);
    return s;
  }

  private void iterate(AstNode node, List<String> values) {
    while (node != null) {
      AstNode child = node.getFirstChild();
      if (child != null) {
        iterate(child, values);
      } else {
        if (node.getType() instanceof CxxGrammarImpl == false) {
          values.add(node.getTokenValue());
        }
      }
      node = node.getNextSibling();
    }
  }

  @Test
  public void preprocessorDirectives() {
    assertThat(p).matches(
      "#define IDX 10\n"
      + "array[IDX];");
  }

  @Test
  public void hashhash_related_parsing_problem() {
    assertThat(p).matches(
      "#define CASES CASE(00)\n"
      + "#define CASE(n) case 0x##n:\n"
      + "void foo()  {\n"
      + "switch (1) {\n"
      + "CASES\n"
      + "break;\n"
      + "}\n"
      + "}\n");
  }

  @Test
  public void object_like_macros() {
    assert (serialize(p.parse(
      "#define BUFFER_SIZE 1024\n"
      + "foo = (char *) malloc (BUFFER_SIZE);"))
      .equals("foo = ( char * ) malloc ( 1024 ) ; EOF"));

    assert (serialize(p.parse(
      "#define NUMBERS 1, \\\n"
      + "2, \\\n"
      + "3\n"
      + "int x[] = { NUMBERS };"))
      .equals("int x [ ] = { 1 , 2 , 3 } ; EOF"));

    assert (serialize(p.parse(
      "#define TABLESIZE BUFSIZE\n"
      + "#define BUFSIZE 1024\n"
      + "int i = TABLESIZE;"))
      .equals("int i = 1024 ; EOF"));

    assert (serialize(p.parse(
      "#define A 1 + 1\n"
      + "int i = A;"))
      .equals("int i = 1 + 1 ; EOF"));

    assert (serialize(p.parse(
      "#define A a // Comment\n"
      + "A;"))
      .equals("a ; EOF"));

   //@todo
    // assert (serialize(p.parse(
    //   "#define A_B A/*Comment*/B\n"
    //   +" A_B;"))
    //   .equals("A B ; EOF"));
  }

  @Test
  public void function_like_macros() {
    assert (serialize(p.parse(
      "#define lang_init() c_init()\n"
      + "lang_init();"))
      .equals("c_init ( ) ; EOF"));

    // without whitespace after parameter list
    assert (serialize(p.parse(
      "#define foo(a)x\n"
      + "foo(b)=1;"))
      .equals("x = 1 ; EOF"));

    // with parantheses
    assert (serialize(p.parse(
      "#define isequal(a, b)(a == b)\n"
      + "b = isequal(1,2);"))
      .equals("b = ( 1 == 2 ) ; EOF"));
  }

  @Test
  public void complex_macro_rescanning() {
    assert (serialize(p.parse(
      "#define lang_init std_init\n"
      + "#define std_init() c_init()\n"
      + "lang_init();"))
      .equals("c_init ( ) ; EOF"));

    assert (serialize(p.parse(
      "#define lang_init(x) x = std_init\n"
      + "#define std_init() c_init()\n"
      + "lang_init(c)();"))
      .equals("c = c_init ( ) ; EOF"));

    assert (serialize(p.parse(
      "#define _MSC_VER_WORKAROUND_GUARD 1\n"
      + "#define BOOST_MSVC_WORKAROUND_GUARD 0\n"
      + "#define _MSC_VER 1600\n"
      + "#define BOOST_MSVC _MSC_VER\n"
      + "#define TEST(symbol) symbol ## _WORKAROUND_GUARD\n"
      + "int i=TEST(BOOST_MSVC);"))
      .equals("int i = 0 ; EOF"));

    assert (serialize(p.parse(
      "#define _MSC_VER_WORKAROUND_GUARD 1\n"
      + "#define BOOST_MSVC_WORKAROUND_GUARD 0\n"
      + "#define _MSC_VER 1600\n"
      + "#define BOOST_MSVC _MSC_VER\n"
      + "#define _WORKAROUND_GUARD _XXX\n"
      + "#define TEST(symbol1, symbol2) symbol1 ## symbol2\n"
      + "int i=TEST(BOOST_MSVC, _WORKAROUND_GUARD);"))
      .equals("int i = 0 ; EOF"));
  }

  @Test
  public void macro_arguments() {
    assert (serialize(p.parse(
      "#define min(X, Y)  ((X) < (Y) ? (X) : (Y))\n"
      + "int i = min(a + 28, *p);"))
      .equals("int i = ( ( a + 28 ) < ( * p ) ? ( a + 28 ) : ( * p ) ) ; EOF"));

    assert (serialize(p.parse(
      "#define foo(x) x + \"x\"\n"
      + "string s = foo(bar);"))
      .equals("string s = bar + \"x\" ; EOF"));
  }

  @Test
  public void variadic_macros() {
    assert (serialize(p.parse(
      "#define eprintf(...) fprintf (stderr, __VA_ARGS__)\n"
      + "eprintf(\"%s:%d: \", input_file, lineno);"))
      .equals("fprintf ( stderr , \"%s:%d: \" , input_file , lineno ) ; EOF"));

    assert (serialize(p.parse(
      "#define eprintf(args...) fprintf (stderr, args)\n"
      + "eprintf(\"%s:%d: \", input_file, lineno);"))
      .equals("fprintf ( stderr , \"%s:%d: \" , input_file , lineno ) ; EOF"));

    assert (serialize(p.parse(
      "#define eprintf(format, ...) fprintf (stderr, format, __VA_ARGS__)\n"
      + "eprintf(\"%s:%d: \", input_file, lineno);"))
      .equals("fprintf ( stderr , \"%s:%d: \" , input_file , lineno ) ; EOF"));

    assert (serialize(p.parse(
      "#define eprintf(format, args...) fprintf (stderr, format, args)\n"
      + "eprintf(\"%s:%d: \", input_file, lineno);"))
      .equals("fprintf ( stderr , \"%s:%d: \" , input_file , lineno ) ; EOF"));

    // the Visual C++ implementation will suppress a trailing comma
    // if no arguments are passed to the ellipsis
    assert (serialize(p.parse(
      "#define EMPTY\n"
      + "#define MACRO(s, ...) printf(s, __VA_ARGS__)\n"
      + "MACRO(\"error\", EMPTY);"))
      .equals("printf ( \"error\" ) ; EOF"));

    assert (serialize(p.parse(
        "#define MACRO(s, ...) printf(s, __VA_ARGS__)\n"
        + "MACRO(\"error\");"))
        .equals("printf ( \"error\" ) ; EOF"));

    // without whitespace after the parameter list
    assert (serialize(p.parse(
      "#define foo(a...);\n"
      + "foo(a, b)"))
      .equals("; EOF"));

    // with more parameters and without whitespace after the parameter list
    assert (serialize(p.parse(
      "#define foo(a, b...);\n"
      + "foo(a, b, c)"))
      .equals("; EOF"));

    // GNU CPP: You are allowed to leave the variable argument out entirely
     assert (serialize(p.parse(
       "#define eprintf(format, ...) fprintf (stderr, format, __VA_ARGS__)\n"
       + "eprintf(\"success!\");"))
       .equals("fprintf ( stderr , \"success!\" ) ; EOF"));
    // GNU CPP: special meaning of token paste operator - if variable argument is left out then the comma before the ‘##’ will be deleted.
    assert (serialize(p.parse(
      "#define eprintf(format, ...) fprintf (stderr, format, ##__VA_ARGS__)\n"
      + "eprintf(\"success!\");"))
      .equals("fprintf ( stderr , \"success!\" ) ; EOF"));
  }

  @Test
  public void stringification() {
    // default use case
    assert (serialize(p.parse(
      "#define make_string(x) #x\n"
      + "string s = make_string(a test);"))
      .equals("string s = \"a test\" ; EOF"));

    // leading and trailing spaces were trimmed,
    // space between words was compressed to a single space character
    assert (serialize(p.parse(
      "#define make_string(x) #x\n"
      + "string s = make_string(   a    test   );"))
      .equals("string s = \"a test\" ; EOF"));

    // the quotes were automatically converted
    assert (serialize(p.parse(
      "#define make_string(x) #x\n"
      + "string s = make_string(\"a\" \"test\");"))
      .equals("string s = \"\\\"a\\\" \\\"test\\\"\" ; EOF"));

    // the slash were automatically converted
    assert (serialize(p.parse(
      "#define make_string(x) #x\n"
      + "string s = make_string(a\\test);"))
      .equals("string s = \"a\\\\test\" ; EOF"));

    // If the token is a macro, the macro is not expanded
    // - the macro name is converted into a string.
    assert (serialize(p.parse(
      "#define make_string(x) #x\n"
      + "#define COMMA ,\n"
      + "string s = make_string(a COMMA test);"))
      .equals("string s = \"a COMMA test\" ; EOF"));

    assert (serialize(p.parse(
      "#define F abc\n"
      + "#define B def\n"
      + "#define FB(arg) #arg\n"
      + "string s = FB(F B);"))
      .equals("string s = \"F B\" ; EOF"));

    assert (serialize(p.parse(
      "#define F abc\n"
      + "#define B def\n"
      + "#define FB(arg) #arg\n"
      + "#define FB1(arg) FB(arg)\n"
      + "string s = FB1(F B);"))
      .equals("string s = \"abc def\" ; EOF"));

    assert (serialize(p.parse(
      "#define F abc\n"
      + "#define B def\n"
      + "#define FB(arg) #arg\n"
      + "#define FB1(arg) FB(arg)\n"
      + "string s = FB1(F\\B);"))
      .equals("string s = \"abc\\\\def\" ; EOF"));

    assert (serialize(p.parse(
      "#define F abc\n"
      + "#define B def\n"
      + "#define FB(arg) #arg\n"
      + "#define FB1(arg) FB(arg)\n"
      + "string s = FB1(F/B);"))
      .equals("string s = \"abc/def\" ; EOF"));
    
    assert (serialize(p.parse(
      "#define SC_METHOD(func) declare_method_process( func ## _handle, #func, func )\n"
      + "SC_METHOD(test);"))
      .equals("declare_method_process ( test_handle , \"test\" , test ) ; EOF"));
  }

  @Test
  public void concatenation() {
    assert (serialize(p.parse(
      "#define A t ## 1\n"
      + "int i = A;"))
      .equals("int i = t1 ; EOF"));

    assert (serialize(p.parse(
      "#define A(p) p ## 1\n"
      + "t = A(t);"))
      .equals("t = t1 ; EOF"));

    assert (serialize(p.parse(
      "#define macro_start i ## n ##t m         ##ain(void);\n"
      + "macro_start"))
      .equals("int main ( void ) ; EOF"));

    assert (serialize(p.parse(
      "#define A B(cf)\n"
      + "#define B(n) 0x##n\n"
      + "i = A;"))
      .equals("i = 0xcf ; EOF"));
  }

  @Test
  public void undef() {
    assert (serialize(p.parse(
      "#define FOO 4\n"
      + "#undef FOO\n"
      + "x = FOO;"))
      .equals("x = FOO ; EOF"));

    assert (serialize(p.parse(
      "#define BUFSIZE 1020\n"
      + "#define TABLESIZE BUFSIZE\n"
      + "#undef BUFSIZE\n"
      + "#define BUFSIZE 37\n"
      + "int i = TABLESIZE;"))
      .equals("int i = 37 ; EOF"));
  }

  @Test
  public void redefining_macros() {
    assert (serialize(p.parse(
      "#define FOO 1\n"
      + "#define FOO 2\n"
      + "int i = FOO;"))
      .equals("int i = 2 ; EOF"));
  }

  @Test
  public void prescan() {
    assert (serialize(p.parse(
      "#define AFTERX(x) X_ ## x\n"
      + "#define XAFTERX(x) AFTERX(x)\n"
      + "#define TABLESIZE 1024\n"
      + "#define BUFSIZE TABLESIZE\n"
      + "int i = XAFTERX(BUFSIZE);"))
      .equals("int i = X_1024 ; EOF"));

    assert (serialize(p.parse(
      "#define FOOBAR 1\n"
      + "#define CHECK(a, b) (( a ## b + 1 == 2))\n"
      + "#if CHECK(FOO , BAR)\n"
      + "i = 1;\n"
      + "#else\n"
      + "0\n"
      + "#endif"))
      .equals("i = 1 ; EOF"));
  }

  @Test
  public void self_referential_macros() {
    assert (serialize(p.parse(
      "#define EPERM EPERM\n"
      + "a = EPERM;"))
      .equals("a = EPERM ; EOF"));

    assert (serialize(p.parse(
      "#define foo (4 + foo)\n"
      + "i = foo;"))
      .equals("i = ( 4 + foo ) ; EOF"));

    assert (serialize(p.parse(
      "#define x (4 + y)\n"
      + "#define y (2 * x)\n"
      + "i = x;"))
      .equals("i = ( 4 + ( 2 * x ) ) ; EOF"));
  }
}
