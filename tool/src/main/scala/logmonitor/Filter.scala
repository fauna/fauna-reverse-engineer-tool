package fauna.tool.logmonitor

import fauna.tool.ast.Effect
import fauna.tool.ast.Expr

import fauna.tool.codegen.Generator
import scala.util.matching.Regex
import fauna.tool.ast.Literal

trait Filter {
  def apply(entry: LogEntry, expr: Option[Expr]): Boolean
}

case class QueryIsHTTPRequestFilter() extends Filter {

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean =
    entry.request.path == "/" && entry.request.method == "POST"
}

case class HttpResponseFilter(code: Int) extends Filter {

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean =
    entry.response.code == code
}

case class HttpErrorResponseFilter(hasError: Boolean) extends Filter {

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean =
    hasError && entry.response.errors.isDefined
}

case class TimeStampFilter(startTs: String, endTs: Option[String]) extends Filter {
  import java.time.OffsetDateTime

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean = {
    val start = OffsetDateTime.parse(startTs)

    if (entry.ts.isDefined) {
      val queryTs = OffsetDateTime.parse(entry.ts.get)
      val afterStart: Boolean = queryTs.isAfter(start)
      if (endTs.isDefined) {
        val end = OffsetDateTime.parse(endTs.get)
        afterStart && queryTs.isBefore(end)
      } else {
        afterStart
      }
    } else {
      false
    }
  }
}

case class QueryFilter(functionNames: Seq[String]) extends Filter {

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean = {
    val d: Boolean = false
    var b: Boolean = false
    expr.fold(d)((e: Expr) => {
      e.forEachChildren((child: Expr) => {
        if (functionNames.contains(child.name)) {
          b = true
        }
      })
      b
    })
  }

}

case class QueryContainsExprFilter(
  search: Expr,
  ignoreLiterals: Boolean,
  ignoredFunctionNames: Seq[String]
) extends Filter {

  def predicate(expr: Expr): Boolean = expr match {
    case _: Literal if ignoreLiterals                     => false
    case e: Expr if ignoredFunctionNames.contains(e.name) => false
    case _                                                => true
  }

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean = {
    expr.fold(false)(Expr.contains(_, search)(predicate))
  }
}

case class QueryRegexFilter(codegen: Generator, _pattern: String) extends Filter {

  val pattern: Regex = s"${_pattern}".r

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean = expr match {
    case None    => false
    case Some(e) => pattern.matches(codegen.exprToCode(e))
  }
}

case class QueryDriverFilter(driverNames: Seq[String]) extends Filter {

  lazy val drivers: Seq[String] = driverNames.map(_.toLowerCase)

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean =
    if (drivers.isEmpty) true
    else entry.driver.fold(false)(d => drivers.contains(d.toLowerCase))
}

case class AuthFilter(key: Option[String], role: Option[String]) extends Filter {
  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean = ???
}

case class QueryEffectFilter(effect: Effect) extends Filter {

  override def apply(entry: LogEntry, expr: Option[Expr]): Boolean =
    expr.fold(false)(_.effect == effect)
}
