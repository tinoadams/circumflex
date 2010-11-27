package ru.circumflex.me

import java.lang.StringBuilder
import java.util.regex._

/*!# Character protector

We use character protector mechanism to ensure that certain elements of markup,
such as inline HTML blocks, remain undamaged when processing.
*/
class Protector {
  protected var protectHash: Map[String, CharSequence] = Map()
  protected var unprotectHash: Map[CharSequence, String] = Map()

  /**
   * Generates a random hash key.
   */
  def randomKey = (0 to keySize).foldLeft("")((s, i) =>
    s + chars.charAt(rnd.nextInt(keySize)))

  /**
   * Adds the specified token to hash and returns the protection key.
   */
  def addToken(t: CharSequence): String = unprotectHash.get(t) match {
    case Some(key) => key
    case _ =>
      val key = randomKey
      protectHash += key -> t
      unprotectHash += t -> key
      key
  }

  /**
   * Attempts to retrieve an encoded sequence by specified `key`.
   */
  def decode(key: String): Option[CharSequence] = protectHash.get(key)

  /**
   * Returns hash keys that are currently in use.
   */
  def keys = protectHash.keys

  override def toString = protectHash.toString
}


class Text(protected val buffer: StringBuilder) {
  def this(cs: CharSequence) = this(new StringBuilder(cs))

  def replaceAll(pattern: Pattern, replacement: Matcher => CharSequence): this.type = {
    var startIndex = 0
    val m = pattern.matcher(buffer)
    while (m.find(startIndex)) {
      val r = replacement(m)
      startIndex = m.start + r.length
      buffer.replace(m.start, m.end, r.toString)
    }
    return this
  }

  override def toString = buffer.toString 
}