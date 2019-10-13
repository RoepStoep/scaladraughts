package draughts
package format
package pdn

import scala._

case class Pdn(
    tags: Tags,
    turns: List[Turn],
    initial: Initial = Initial.empty
) {

  def updateTurn(fullMove: Int, f: Turn => Turn) = {
    val index = fullMove - 1
    (turns lift index).fold(this) { turn =>
      copy(turns = turns.updated(index, f(turn)))
    }
  }
  def updatePly(ply: Int, f: Move => Move) = {
    val fullMove = (ply + 1) / 2
    val color = Color(ply % 2 == 1)
    updateTurn(fullMove, _.update(color, f))
  }
  def updateLastPly(f: Move => Move) = updatePly(nbPlies, f)

  def nbPlies = turns.foldLeft(0)(_ + _.count)

  def moves = turns.flatMap { t =>
    List(t.white, t.black).flatten
  }

  def withEvent(title: String) = copy(
    tags = tags + Tag(_.Event, title)
  )

  def render: String = {
    val initStr =
      if (initial.comments.nonEmpty) initial.comments.mkString("{ ", " } { ", " }\n")
      else ""
    val turnStr = turns mkString " "
    val endStr = tags(_.Result) getOrElse ""
    s"$tags\n\n$initStr$turnStr $endStr"
  }.trim + "\n"

  override def toString = render
}

case class Initial(comments: List[String] = Nil)

object Initial {
  val empty = Initial(Nil)
}

case class Turn(
    number: Int,
    white: Option[Move],
    black: Option[Move]
) {

  def update(color: Color, f: Move => Move) = color.fold(
    copy(white = white map f),
    copy(black = black map f)
  )

  def updateLast(f: Move => Move) = {
    black.map(m => copy(black = Some(f(m)))) orElse
      white.map(m => copy(white = Some(f(m))))
  } getOrElse this

  def isEmpty = white.isEmpty && black.isEmpty

  def plyOf(color: Color) = number * 2 - color.fold(1, 0)

  def count = List(white, black) count (_.isDefined)

  override def toString = {
    val text = (white, black) match {
      case (Some(w), Some(b)) if w.isLong => s" $w $number... $b"
      case (Some(w), Some(b)) => s" $w $b"
      case (Some(w), None) => s" $w"
      case (None, Some(b)) => s".. $b"
      case _ => ""
    }
    s"$number.$text"
  }
}

object Turn {

  def fromMoves(moves: List[Move], ply: Int): List[Turn] = {
    moves.foldLeft((List[Turn](), ply)) {
      case ((turns, p), move) if p % 2 == 1 =>
        (Turn((p + 1) / 2, Some(move), None) :: turns) -> (p + 1)
      case ((Nil, p), move) =>
        (Turn((p + 1) / 2, None, Some(move)) :: Nil) -> (p + 1)
      case ((t :: tt, p), move) =>
        (t.copy(black = Some(move)) :: tt) -> (p + 1)
    }
  }._1.reverse
}

case class Move(
    san: String,
    // the color who played the move
    turn: Color,
    comments: List[String] = Nil,
    glyphs: Glyphs = Glyphs.empty,
    opening: Option[String] = None,
    result: Option[String] = None,
    variations: List[List[Turn]] = Nil,
    // time left for the white, black player, after the move is made
    secondsLeft: (Option[Int], Option[Int]) = (None, None)
) {

  def isLong = comments.nonEmpty || variations.nonEmpty

  private def clockString: Option[String] =
    if (secondsLeft._1.isDefined && secondsLeft._2.isDefined)
      Some(s"[%clock ${turn.fold("w", "W")}${Move.formatPdnSeconds(secondsLeft._1.get)} ${turn.fold("B", "b")}${Move.formatPdnSeconds(secondsLeft._2.get)}]")
    else None

  override def toString = {
    val glyphStr = glyphs.toList.map({
      case glyph if glyph.id <= 6 => glyph.symbol
      case glyph => s" $$${glyph.id}"
    }).mkString
    val commentsOrTime =
      if (comments.nonEmpty || (secondsLeft._1.isDefined && secondsLeft._2.isDefined) || opening.isDefined || result.isDefined)
        List(clockString, opening, result).flatten.:::(comments map Move.noDoubleLineBreak).map { text =>
          s" {$text}"
        }.mkString
      else ""
    val variationString =
      if (variations.isEmpty) ""
      else variations.map(_.mkString(" (", " ", ")")).mkString(" ")
    s"$san$glyphStr$commentsOrTime$variationString"
  }
}

object Move {

  private val noDoubleLineBreakRegex = "(\r?\n){2,}".r

  private def noDoubleLineBreak(txt: String) =
    noDoubleLineBreakRegex.replaceAllIn(txt, "\n")

  private def formatPdnSeconds(t: Int) = s"${t}s"

}