package com.bingo.polyglot.worker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WerUtilTest {
  @Test
  fun `perfect match`() {
    val wer = WerUtil.calculate("hello world", "hello world")
    assertEquals(0.0, wer)
  }

  @Test
  fun `one deletion`() {
    val wer = WerUtil.calculate("hello world", "hello")
    assertEquals(0.5, wer)
  }

  @Test
  fun `one insertion`() {
    val wer = WerUtil.calculate("hello", "hello world")
    assertEquals(1.0, wer)
  }

  @Test
  fun `one substitution`() {
    val wer = WerUtil.calculate("hello world", "hello word")
    assertEquals(0.5, wer)
  }

  @Test
  fun `completely different`() {
    val wer = WerUtil.calculate("a b c", "x y z")
    assertEquals(1.0, wer)
  }

  @Test
  fun `both empty`() {
    val wer = WerUtil.calculate("", "")
    assertEquals(0.0, wer)
  }

  @Test
  fun `reference empty`() {
    val wer = WerUtil.calculate("", "hello world")
    assertEquals(1.0, wer)
  }

  @Test
  fun `hypothesis empty`() {
    val wer = WerUtil.calculate("hello world", "")
    assertEquals(1.0, wer)
  }

  @Test
  fun `extra spaces and trimming`() {
    val wer = WerUtil.calculate("  hello   world  ", "hello world")
    assertEquals(0.0, wer)
  }

  @Test
  fun `complex sentence`() {
    val wer =
      WerUtil.calculate(
        "Tilly, a little fox, loved her bright red balloon. She carried it everywhere.\n“It’s my favorite balloon!” Tilly said.\n",
        """Tilly, a little fox, loved her bright red balloon.
She carried it everywhere.
My name's Tilly.
It's my favorite balloon.
Tilly said.
    """,
      )
    assertEquals(0.2631578947368421, wer)
  }
}
