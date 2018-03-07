
def hasRuntime(File file) {
	file.exists() && file.isFile() && file.canRead() && file.canExecute()
}

def assertRuntime(File file) {
	assert hasRuntime(file)
	def execPath = file.getAbsolutePath()
	println("path: ${execPath}")
	def execText = execPath.execute().text
	def testText = "scala-native"
	println("text: ${execText}")
	assert execText.contains(testText)
}

def mainOut = "target/scalor/native/output/main"

def mainDebug = "${mainOut}/debug"
def mainRelease = "${mainOut}/release"

def mainDebug211 = new File("${basedir}/2.11/${mainDebug}")
def mainRelease211 = new File("${basedir}/2.11/${mainRelease}")

assertRuntime(mainDebug211)
assertRuntime(mainRelease211)

assert true
