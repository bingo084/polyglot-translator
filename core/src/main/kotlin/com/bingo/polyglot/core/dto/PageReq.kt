package com.bingo.polyglot.core.dto

import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.kt.ast.query.KConfigurableRootQuery
import org.babyfish.jimmer.sql.kt.ast.query.KMutableRootQuery
import org.babyfish.jimmer.sql.kt.ast.table.makeOrders
import java.sql.Connection

/**
 * Pagination request parameters.
 *
 * @author bingo
 */
data class PageReq(
  /** Page number, starting from 1 */
  val pageNum: Int = 1,
  /** Page size */
  val pageSize: Int = 10,
  /** Sort expression, e.g., "id desc" */
  val sortCode: String = "id desc",
)

/**
 * Fetch a paginated result based on the given PageReq.
 *
 * @param pageReq Pagination request object
 * @param con Optional SQL connection
 * @return Paginated result
 */
fun <E> KConfigurableRootQuery<*, E>.fetchPage(pageReq: PageReq, con: Connection? = null): Page<E> =
  fetchPage(pageReq.pageNum - 1, pageReq.pageSize, con)

/**
 * Apply ordering to the query based on the PageReq.
 *
 * @param pageReq Pagination request containing the sort code
 */
fun <E : Any> KMutableRootQuery<E>.orderBy(pageReq: PageReq) =
  orderBy(table.makeOrders(pageReq.sortCode))
