/*
 * Copyright 2019 Jungtaek Lim "<kabhwan@gmail.com>"
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.heartsavior.spark.sql.state

import net.heartsavior.spark.sql.util.SchemaUtil
import org.apache.hadoop.fs.Path

import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession, SQLContext}
import org.apache.spark.sql.execution.streaming.state.StateStoreId
import org.apache.spark.sql.hack.SparkSqlHack
import org.apache.spark.sql.sources.{BaseRelation, TableScan}
import org.apache.spark.sql.types.StructType

// TODO: read schema of key and value from metadata of state (requires SPARK-27237)
class StateStoreRelation(
    session: SparkSession,
    keySchema: StructType,
    valueSchema: StructType,
    stateCheckpointLocation: String,
    batchId: Int,
    operatorId: Int,
    storeName: String = StateStoreId.DEFAULT_STORE_NAME)
  extends BaseRelation with TableScan with Logging {

  override def sqlContext: SQLContext = session.sqlContext

  override def schema: StructType = SchemaUtil.keyValuePairSchema(keySchema, valueSchema)

  override def buildScan(): RDD[Row] = {
    val resolvedCpLocation = {
      val checkpointPath = new Path(stateCheckpointLocation)
      val fs = checkpointPath.getFileSystem(SparkSqlHack.sessionState(sqlContext).newHadoopConf())
      fs.mkdirs(checkpointPath)
      checkpointPath.makeQualified(fs.getUri, fs.getWorkingDirectory).toUri.toString
    }

    new StateStoreReaderRDD(session, keySchema, valueSchema,
      resolvedCpLocation, batchId, operatorId, storeName)
  }
}
