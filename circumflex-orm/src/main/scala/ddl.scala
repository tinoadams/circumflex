package ru.circumflex.orm

import java.sql.Connection
import JDBC._
import ORM._

/**
 * A Unit-of-Work for generating database schema.
 */
class DDLUnit {

  protected var _schemata: Seq[Schema] = Nil
  def schemata = _schemata
  protected var _tables: Seq[Table[_]] = Nil
  def tables = _tables
  protected var _views: Seq[View[_]] = Nil
  def views = _views
  protected var _constraints: Seq[Constraint] = Nil
  def constraints = _constraints
  protected var _preAux: Seq[SchemaObject] = Nil
  def preAux = _preAux
  protected var _postAux: Seq[SchemaObject] = Nil
  def postAux = _postAux

  protected var _infoMsgs: Seq[Pair[String, String]] = Nil
  def infoMsgs = _infoMsgs
  protected var _errorMsgs: Seq[Pair[String, String]] = Nil
  def errorMsgs = _errorMsgs

  def reset() = {
    _infoMsgs = Nil
    _errorMsgs = Nil
  }

  def clear() = {
    _schemata = Nil
    _tables = Nil
    _views = Nil
    _constraints = Nil
    _preAux = Nil
    _postAux = Nil
    reset()
  }

  def add(objects: SchemaObject*): this.type = {
    objects.foreach(addObject(_))
    return this
  }

  def addObject(obj: SchemaObject): this.type = {
    def processRelation(r: Relation[_]) = {
      addObject(r.schema)
      r.preAux.foreach(o =>
        if (!_preAux.contains(o)) _preAux ++= List(o))
      r.postAux.foreach(o => addObject(o))
    }
    obj match {
      case t: Table[_] => if (!_tables.contains(t)) {
        _tables ++= List(t)
        t.constraints.foreach(o => addObject(o))
        processRelation(t)
      }
      case v: View[_] => if (!_views.contains(v)) {
        _views ++= List(v)
        processRelation(v)
      }
      case c: Constraint => if (!_constraints.contains(c))
        _constraints ++= List(c)
      case s: Schema => if (!_schemata.contains(s))
        _schemata ++= List(s)
      case o => if (!_postAux.contains(o))
        _postAux ++= List(o)
    }
    return this
  }

  protected def dropObjects(objects: Seq[SchemaObject], conn: Connection) =
    for (o <- objects.reverse)
      autoClose(conn.prepareStatement(o.sqlDrop))(st => {
        st.executeUpdate
        _infoMsgs ++= List("DROP "  + o.objectName + ": OK" -> o.sqlDrop)
      })(e => {
        _errorMsgs ++= List("DROP " + o.objectName + ": " + e.getMessage -> o.sqlDrop)
      })

  protected def createObjects(objects: Seq[SchemaObject], conn: Connection) =
    for (o <- objects)
      autoClose(conn.prepareStatement(o.sqlCreate))(st => {
        st.executeUpdate
        _infoMsgs ++= List("CREATE " + o.objectName + ": OK" -> o.sqlCreate)
      })(e => {
        _errorMsgs ++= List("CREATE " + o.objectName + ": " + e.getMessage -> o.sqlCreate)
      })

  /**
   * Execute a DROP script for added objects.
   */
  def drop(): Unit = auto(tx.connection)(conn => {
    // We will commit every successfull statement.
    val autoCommit = conn.getAutoCommit
    conn.setAutoCommit(true)
    // Execute a script.
    dropObjects(postAux, conn)
    dropObjects(views, conn)
    if (dialect.supportsDropConstraints_?)
      dropObjects(constraints, conn)
    dropObjects(tables, conn)
    dropObjects(preAux, conn)
    if (dialect.supportsSchema_?)
      dropObjects(schemata, conn)
    // Restore auto-commit.
    conn.setAutoCommit(autoCommit)
  })

  /**
   * Execute a CREATE script for added objects.
   */
  def create(): Unit = auto(tx.connection)(conn => {
    // We will commit every successfull statement.
    val autoCommit = conn.getAutoCommit
    conn.setAutoCommit(true)
    // Execute a script.
    if (dialect.supportsSchema_?)
      createObjects(schemata, conn)
    createObjects(preAux, conn)
    createObjects(tables, conn)
    createObjects(constraints, conn)
    createObjects(views, conn)
    createObjects(postAux, conn)
    // Restore auto-commit.
    conn.setAutoCommit(autoCommit)
  })

  /**
   * Execute a DROP script and then a CREATE script.
   */
  def dropCreate(): Unit = {
    drop()
    create()
  }

}