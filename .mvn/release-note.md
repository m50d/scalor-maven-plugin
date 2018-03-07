
--- 1.4.0 ---

#### New features
* initial Scala.native support

#### Breaking change
* change some `scalor.*` parameter names

#### Improvements
* update `scala-js-junit-tools`
* explicit JavaC compiler options for Zinc invocation
* enable `env-conf-*` and `env-prov-*` goals in Eclipse
* use constant default options for JavaC and ScalaC #14
* use separate source regex for compile and scaladoc #13

#### Bug fixes
* inject `scala-js-junit-tools` basedir config
* relax bundle version of `org.scala-lang.modules.scala-xml`
* relax plugin version verification rules #10
* work around nsc.Settings ignoring `-D` user-set state #11
* proper implementation for `scala.compiler.additionalParams` #12

--- 1.3.0 ---

#### Bug fixes
* some `scalor.*` parameter names

#### Improvements
* code cleanup
* new arkon goals: `clean, compile, register, ...`
* new formatter goals: `format, format-macro, format-main, format-test`

--------------
