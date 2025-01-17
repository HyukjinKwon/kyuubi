/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.sql.trino

import org.antlr.v4.runtime._
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.misc.ParseCancellationException

import org.apache.kyuubi.sql.{KyuubiSqlBaseLexer, KyuubiTrinoBaseParser}
import org.apache.kyuubi.sql.parser.{PostProcessor, UpperCaseCharStream}
import org.apache.kyuubi.sql.plan.KyuubiTreeNode

class KyuubiTrinoParser {

  lazy val astBuilder = new TrinoStatementAstBuilder

  def parsePlan(sqlText: String): KyuubiTreeNode = parse(sqlText) { parser =>
    astBuilder.visit(parser.singleStatement) match {
      case plan: KyuubiTreeNode => plan
    }
  }

  protected def parse[T](command: String)(toResult: KyuubiTrinoBaseParser => T): T = {
    val lexer = new KyuubiSqlBaseLexer(
      new UpperCaseCharStream(CharStreams.fromString(command)))
    lexer.removeErrorListeners()

    val tokenStream = new CommonTokenStream(lexer)
    val parser = new KyuubiTrinoBaseParser(tokenStream)
    parser.addParseListener(PostProcessor)
    parser.removeErrorListeners()

    try {
      // first, try parsing with potentially faster SLL mode
      parser.getInterpreter.setPredictionMode(PredictionMode.SLL)
      toResult(parser)
    } catch {
      case _: ParseCancellationException =>
        // if we fail, parse with LL mode
        tokenStream.seek(0) // rewind input stream
        parser.reset()

        // Try Again.
        parser.getInterpreter.setPredictionMode(PredictionMode.LL)
        toResult(parser)
    }
  }
}
