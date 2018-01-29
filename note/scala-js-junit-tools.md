
### Scala.js JUnit Tools

See [ScalaJSJUnitPlugin.scala](https://github.com/scala-js/scala-js/blob/master/junit-plugin/src/main/scala/org/scalajs/junit/plugin/ScalaJSJUnitPlugin.scala)

See [JUnitTestBootstrapper.scala](https://github.com/scala-js/scala-js/blob/51b537ae4c64783db016ca3e90e675a895d50992/junit-runtime/src/main/scala/org/scalajs/junit/JUnitTestBootstrapper.scala)

Original Scala class

```scala
class Test01 {
  @Test
  def verifyPrint() {
    println( s"### Message from JS-VM ${getClass.getName} ###" )
  }
  @Test
  def verifyVM() {
    assertEquals( "Running in Scala.js VM", System.getProperty( "java.vm.name" ), "Scala.js" )
  }
}
```

Decompiled Scala class

```java
public class Test01
{
  @Test
  public void verifyPrint()
  {
    Predef..MODULE$.println(new StringContext(Predef..MODULE$.wrapRefArray((Object[])new String[] { "### Message from JS-VM ", " ###" })).s(Predef..MODULE$.genericWrapArray(new Object[] { getClass().getName() })));
  }
  @Test
  public void verifyVM()
  {
    Assert.assertEquals("Running in Scala.js VM", System.getProperty("java.vm.name"), "Scala.js");
  }
  public Test01() {}
}
```

Bootstrapper companion class generated by `scalajs-junit-test-plugin`

```java
public final class Test01$scalajs$junit$bootstrapper
{
  public static void invoke(Object paramObject, String paramString)
  {
    Test01.scalajs.junit.bootstrapper..MODULE$.invoke(paramObject, paramString);
  }
  
  public static void invoke(String paramString)
  {
    Test01.scalajs.junit.bootstrapper..MODULE$.invoke(paramString);
  }
  
  public static Object newInstance()
  {
    return Test01.scalajs.junit.bootstrapper..MODULE$.newInstance();
  }
  
  public static JUnitClassMetadata metadata()
  {
    return Test01.scalajs.junit.bootstrapper..MODULE$.metadata();
  }
  
  @Test
  public static void verifyVM()
  {
    Test01.scalajs.junit.bootstrapper..MODULE$.verifyVM();
  }
  
  @Test
  public static void verifyPrint()
  {
    Test01.scalajs.junit.bootstrapper..MODULE$.verifyPrint();
  }
}
```

Bootstrapper companion module generated by `scalajs-junit-test-plugin`

```java
public final class Test01$scalajs$junit$bootstrapper$
  implements JUnitTestBootstrapper
{
  public static  MODULE$;
  
  static
  {
    new ();
  }
  
  public JUnitClassMetadata metadata()
  {
    return new JUnitClassMetadata(List..MODULE$.apply(Predef..MODULE$.wrapRefArray(new Object[0])), Nil..MODULE$, List..MODULE$.apply(Predef..MODULE$.wrapRefArray(new Object[] { new JUnitMethodMetadata("verifyVM", List..MODULE$.apply(Predef..MODULE$.wrapRefArray(new Object[] { new Test() }))), new JUnitMethodMetadata("verifyPrint", List..MODULE$.apply(Predef..MODULE$.wrapRefArray(new Object[] { new Test() }))) })), Nil..MODULE$);
  }
  
  public Object newInstance()
  {
    return new Test01();
  }
  
  public void invoke(String methodName)
  {
    throw new NoSuchMethodException(methodName + " not found");
  }
  
  public void invoke(Object instance, String methodName)
  {
    if (methodName.equals("verifyVM")) {
      ((Test01)instance).verifyVM();
    } else if (methodName.equals("verifyPrint")) {
      ((Test01)instance).verifyPrint();
    } else {
      throw new NoSuchMethodException(methodName + " not found");
    }
  }
}
```