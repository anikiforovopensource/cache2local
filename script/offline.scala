// Copies Ivy cache as a local repository.

import java.io._


// Helper functions.
def file(s: String) = new File(s)

def traverse(dir: File)(visit: File => Unit): Unit = if (dir.exists) {
  for (file <- dir.listFiles.sortBy(_.getName)) {
    if (file.isDirectory) {
      traverse(file)(visit)
      visit(file)
    }
    else visit(file)
  }
}

def filtered(dir: File, exclude: Set[String])(visit: File => Unit) = traverse(dir) { file =>
  if (!file.isDirectory && !exclude.exists(file.getName.endsWith)) {
    visit(file)
  }
}


val buffer = new Array[Byte](1024)
def copy(op: (File, File)): Unit = op match { case (from, to) =>
  println(s"Writing file: ${to.getCanonicalPath}")
  to.getParentFile.mkdirs
  var in: InputStream = null
  var out: OutputStream = null
  try {
    in = new BufferedInputStream(new FileInputStream(from))
    out = new BufferedOutputStream(new FileOutputStream(to))
    var read = 0; while (read >= 0) {
      out.write(buffer, 0, read)
      read = in.read(buffer)
    }
  }
  finally {
    Option(in).foreach { s => try { s.close() } catch { case e: IOException => /* ignore */ }}
    Option(out).foreach { s => try { s.close() } catch { case e: IOException => /* ignore */ }}
  }
}

def rewrite(op: (File, File)): Unit = op match { case (from, to) =>
  require(from.getName.endsWith(".xml"))
  println(s"Writing file: ${to.getCanonicalPath}")
  to.getParentFile.mkdirs
  var out: OutputStreamWriter = null
  try {
    val text = scala.io.Source.fromFile(from).getLines.mkString("\n")
    val sbtCruft = """<sbtTransformHash>.*</sbtTransformHash>"""
    val fixed = text.replaceAll(sbtCruft, "")
    out = new OutputStreamWriter(new FileOutputStream(to))
    out.write(fixed)
  }
  finally {
    Option(out).foreach { s => try { s.close() } catch { case e: IOException => /* ignore */ }}
  }
}


// Dir setup.
val targetDir = file(System.getProperty("user.home") + "/.ivy2/local")
val target = targetDir.getCanonicalPath

val cacheDir = file(System.getProperty("user.home") + "/.ivy2/backup")
val cache = cacheDir.getCanonicalPath


// Regex parsing setup.
val ver = """(\d+(?:\.\d+)?(?:\.\d+)?(?:-RC\d+|-rc\d+|-M\d+|-m\d+)?)"""

val org = """([^/]+)"""
val module = """([a-zA-Z0-9_-]+)"""
val scalav = s"""(?:_$ver)?"""
val types = """(\w+s)"""
val artifact = """([^/_]+[a-zA-Z0-9])"""
val rev = s"""-$ver"""
val classifier = """(?:-(\w+))?"""
val ext = """\.(\w+)"""
val dot = """\."""

val IvyXml = s"""$cache/${org}/${module}${scalav}/ivy${rev}${dot}xml""".r
val Artifact = (s"""$cache/${org}/${module}${scalav}/""" +
  s"""${types}/${artifact}${scalav}${rev}${classifier}${ext}""").r
  
val PluginIvyXml = s"""$cache/scala_${ver}/sbt_${ver}/${org}/${module}/ivy${rev}${dot}xml""".r
val PluginArtifact = (s"""$cache/scala_${ver}/sbt_${ver}/${org}/${module}/""" +
  s"""${types}/${artifact}${rev}${classifier}${ext}""").r

val Relative = s"""$cache/(.*)""".r


// Cache copying code.
def op(p: String, s: String) = Option(s).map(p + _).getOrElse("")

val exclude = Set(".original", ".properties", ".part")
filtered(cacheDir, exclude) { source =>
  source.getCanonicalPath match {
    
    case IvyXml(org, module, scalaVer, rev) =>
      val dest = s"""$target/${org}/${module}${op("_", scalaVer)}/${rev}/ivys/ivy.xml"""
      rewrite(source -> file(dest))
    
    case Artifact(org, module, scalaVer, types, artifact, scalaVerX, rev, classifier, ext) =>
      val dest = s"""$target/${org}/${module}${op("_", scalaVer)}/""" + 
        s"""${rev}/${types}/${artifact}${op("_", scalaVer)}${op("-", classifier)}.${ext}"""
      copy(source -> file(dest))
        
    case PluginIvyXml(scalaVer, sbtVer, org, module, rev) =>
      val dest = s"""$target/${org}/${module}${op("/scala_", scalaVer)}${op("/sbt_", sbtVer)}/""" +
        s"""${rev}/ivys/ivy.xml"""
      rewrite(source -> file(dest))
    
    case PluginArtifact(scalaVer, sbtVer, org, module, types, artifact, rev, classifier, ext) =>
      val dest = s"""$target/${org}/${module}${op("/scala_", scalaVer)}${op("/sbt_", sbtVer)}/""" + 
        s"""${rev}/${types}/${artifact}${op("-", classifier)}.${ext}"""
      copy(source -> file(dest))
      
    case Relative(path) =>
      println(s"ERR: $path")
  }
}
