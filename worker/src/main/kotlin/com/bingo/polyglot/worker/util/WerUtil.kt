package com.bingo.polyglot.worker.util

/**
 * Utility object for calculating Word Error Rate (WER) between two texts.
 *
 * WER measures the difference between a reference and a hypothesis by computing the minimum number
 * of word-level edits required.
 *
 * @author bingo
 */
object WerUtil {

  /**
   * Calculates the Word Error Rate (WER) between the reference text and the hypothesis text. WER =
   * (Number of edits) / (Number of words in reference)
   *
   * @param reference The original (correct) text.
   * @param hypothesis The recognized or generated text.
   * @return The WER as a Double between 0.0 (perfect match) and 1.0 (completely different). Returns
   *   0.0 if both texts are empty, and 1.0 if reference is empty but hypothesis is not.
   */
  fun calculate(reference: String, hypothesis: String): Double {
    // Split texts into words, ignoring extra spaces
    val refWords = reference.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val hypWords = hypothesis.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

    // Handle edge cases where reference is empty
    if (refWords.isEmpty()) {
      return if (hypWords.isEmpty()) 0.0 else 1.0
    }

    // Compute Levenshtein distance (edit distance) between the word lists
    val distance = levenshteinDistance(refWords, hypWords)

    // Calculate WER as edits divided by the reference length
    return distance.toDouble() / refWords.size
  }

  /**
   * Computes the Levenshtein distance between two lists of words. This represents the minimum
   * number of single-word edits (insertions, deletions, substitutions) required to change one list
   * into the other.
   *
   * @param a The first list of words (reference).
   * @param b The second list of words (hypothesis).
   * @return The integer edit distance.
   */
  private fun levenshteinDistance(a: List<String>, b: List<String>): Int {
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }

    // Initialize the first row and column of the DP matrix
    for (i in 0..a.size) dp[i][0] = i
    for (j in 0..b.size) dp[0][j] = j

    // Compute distances for all prefixes of a and b
    for (i in 1..a.size) {
      for (j in 1..b.size) {
        dp[i][j] =
          minOf(
            dp[i - 1][j] + 1, // Deletion
            dp[i][j - 1] + 1, // Insertion
            dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, // Substitution or no-op
          )
      }
    }

    // The bottom-right cell is the total edit distance
    return dp[a.size][b.size]
  }
}
