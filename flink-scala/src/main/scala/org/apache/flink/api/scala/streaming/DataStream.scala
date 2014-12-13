/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.scala.streaming
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.datastream.{ DataStream => JavaStream }
import org.apache.flink.api.common.typeinfo.TypeInformation
import scala.reflect.ClassTag
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.invokable.operator.MapInvokable
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.util.Collector
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.invokable.operator.FlatMapInvokable
import org.apache.flink.api.common.functions.ReduceFunction
import org.apache.flink.streaming.api.invokable.StreamInvokable
import org.apache.flink.streaming.api.datastream.GroupedDataStream
import org.apache.flink.streaming.api.invokable.operator.GroupedReduceInvokable
import org.apache.flink.streaming.api.invokable.operator.StreamReduceInvokable
import org.apache.flink.streaming.api.datastream.GroupedDataStream
import org.apache.flink.api.common.functions.ReduceFunction
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.common.functions.FilterFunction
import org.apache.flink.streaming.api.function.sink.SinkFunction

class DataStream[T](javaStream: JavaStream[T]) {

  /* This code is originally from the Apache Spark project. */
  /**
   * Clean a closure to make it ready to serialized and send to tasks
   * (removes unreferenced variables in $outer's, updates REPL variables)
   * If <tt>checkSerializable</tt> is set, <tt>clean</tt> will also proactively
   * check to see if <tt>f</tt> is serializable and throw a <tt>SparkException</tt>
   * if not.
   *
   * @param f the closure to clean
   * @param checkSerializable whether or not to immediately check <tt>f</tt> for serializability
   * @throws <tt>SparkException<tt> if <tt>checkSerializable</tt> is set but <tt>f</tt> is not
   *   serializable
   */
  private[flink] def clean[F <: AnyRef](f: F, checkSerializable: Boolean = true): F = {
    ClosureCleaner.clean(f, checkSerializable)
    f
  }

  /**
   * Gets the underlying java DataStream object.
   */
  private[flink] def getJavaStream: JavaStream[T] = javaStream

  /**
   * Sets the degree of parallelism of this operation. This must be greater than 1.
   */
  def setParallelism(dop: Int) = {
    javaStream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.setParallelism(dop)
      case _ =>
        throw new UnsupportedOperationException("Operator " + javaStream.toString + " cannot have " +
          "parallelism.")
    }
    this
  }

  /**
   * Returns the degree of parallelism of this operation.
   */
  def getParallelism: Int = javaStream match {
    case op: SingleOutputStreamOperator[_, _] => op.getParallelism
    case _ =>
      throw new UnsupportedOperationException("Operator " + javaStream.toString + " does not have " +
        "parallelism.")
  }

  /**
   * Creates a new DataStream by merging DataStream outputs of
   * the same type with each other. The DataStreams merged using this operator
   * will be transformed simultaneously.
   *
   */
  def merge(dataStreams: DataStream[T]*): DataStream[T] =
    new DataStream[T](javaStream.merge(dataStreams.map(_.getJavaStream): _*))

  /**
   * Groups the elements of a DataStream by the given key positions (for tuple/array types) to
   * be used with grouped operators like grouped reduce or grouped aggregations
   *
   */
  def groupBy(fields: Int*): DataStream[T] =
    new DataStream[T](javaStream.groupBy(fields: _*))

  /**
   * Groups the elements of a DataStream by the given field expressions to
   * be used with grouped operators like grouped reduce or grouped aggregations
   *
   */
  def groupBy(firstField: String, otherFields: String*): DataStream[T] =
    new DataStream[T](javaStream.groupBy(firstField +: otherFields.toArray: _*))

  /**
   * Groups the elements of a DataStream by the given K key to
   * be used with grouped operators like grouped reduce or grouped aggregations
   *
   */
  def groupBy[K: TypeInformation](fun: T => K): DataStream[T] = {

    val keyExtractor = new KeySelector[T, K] {
      val cleanFun = clean(fun)
      def getKey(in: T) = cleanFun(in)
    }
    new DataStream[T](javaStream.groupBy(keyExtractor))
  }

  /**
   * Sets the partitioning of the DataStream so that the output is
   * partitioned by the selected fields. This setting only effects the how the outputs will be distributed between the parallel instances of the next processing operator.
   *
   */
  def partitionBy(fields: Int*): DataStream[T] =
    new DataStream[T](javaStream.partitionBy(fields: _*))

  /**
   * Sets the partitioning of the DataStream so that the output is
   * partitioned by the selected fields. This setting only effects the how the outputs will be distributed between the parallel instances of the next processing operator.
   *
   */
  def partitionBy(firstField: String, otherFields: String*): DataStream[T] =
    new DataStream[T](javaStream.partitionBy(firstField +: otherFields.toArray: _*))

  /**
   * Sets the partitioning of the DataStream so that the output is
   * partitioned by the given Key. This setting only effects the how the outputs will be distributed between the parallel instances of the next processing operator.
   *
   */
  def partitionBy[K: TypeInformation](fun: T => K): DataStream[T] = {

    val keyExtractor = new KeySelector[T, K] {
      val cleanFun = clean(fun)
      def getKey(in: T) = cleanFun(in)
    }
    new DataStream[T](javaStream.partitionBy(keyExtractor))
  }

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are broadcasted to every parallel instance of the next component. This
   * setting only effects the how the outputs will be distributed between the
   * parallel instances of the next processing operator.
   *
   */
  def broadcast: DataStream[T] = new DataStream[T](javaStream.broadcast())

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are shuffled to the next component. This setting only effects the how the
   * outputs will be distributed between the parallel instances of the next
   * processing operator.
   *
   */
  def shuffle: DataStream[T] = new DataStream[T](javaStream.shuffle())

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are forwarded to the local subtask of the next component (whenever
   * possible). This is the default partitioner setting. This setting only
   * effects the how the outputs will be distributed between the parallel
   * instances of the next processing operator.
   *
   */
  def forward: DataStream[T] = new DataStream[T](javaStream.forward())

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are distributed evenly to the next component.This setting only effects
   * the how the outputs will be distributed between the parallel instances of
   * the next processing operator.
   *
   */
  def distribute: DataStream[T] = new DataStream[T](javaStream.distribute())

  /**
   * Applies an aggregation that that gives the current maximum of the data stream at
   * the given position.
   *
   */
  def max(field: Any): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.max(field))
    case field: String => return new DataStream[T](javaStream.max(field))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that that gives the current minimum of the data stream at
   * the given position.
   *
   */
  def min(field: Any): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.min(field))
    case field: String => return new DataStream[T](javaStream.min(field))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that sums the data stream at the given position.
   *
   */
  def sum(field: Any): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.sum(field))
    case field: String => return new DataStream[T](javaStream.sum(field))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that that gives the current maximum element of the data stream by
   * the given position. When equality, returns the first.
   *
   */
  def maxBy(field: Any): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.maxBy(field))
    case field: String => return new DataStream[T](javaStream.maxBy(field))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that that gives the current minimum element of the data stream by
   * the given position. When equality, returns the first.
   *
   */
  def minBy(field: Any): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.minBy(field))
    case field: String => return new DataStream[T](javaStream.minBy(field))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that that gives the current minimum element of the data stream by
   * the given position. When equality, the user can set to get the first or last element with the minimal value.
   *
   */
  def minBy(field: Any, first: Boolean): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.minBy(field, first))
    case field: String => return new DataStream[T](javaStream.minBy(field, first))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Applies an aggregation that that gives the current maximum element of the data stream by
   * the given position. When equality, the user can set to get the first or last element with the maximal value.
   *
   */
  def maxBy(field: Any, first: Boolean): DataStream[T] = field match {
    case field: Int => return new DataStream[T](javaStream.maxBy(field, first))
    case field: String => return new DataStream[T](javaStream.maxBy(field, first))
    case _ => throw new IllegalArgumentException("Aggregations are only supported by field position (Int) or field expression (String)")
  }

  /**
   * Creates a new DataStream containing the current number (count) of
   * received records.
   *
   */
  def count: DataStream[java.lang.Long] = new DataStream[java.lang.Long](javaStream.count())

  /**
   * Creates a new DataStream by applying the given function to every element of this DataStream.
   */
  def map[R: TypeInformation: ClassTag](fun: T => R): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("Map function must not be null.")
    }
    val mapper = new MapFunction[T, R] {
      val cleanFun = clean(fun)
      def map(in: T): R = cleanFun(in)
    }

    new DataStream(javaStream.transform("map", implicitly[TypeInformation[R]], new MapInvokable[T, R](mapper)))
  }

  /**
   * Creates a new DataStream by applying the given function to every element of this DataStream.
   */
  def map[R: TypeInformation: ClassTag](mapper: MapFunction[T, R]): DataStream[R] = {
    if (mapper == null) {
      throw new NullPointerException("Map function must not be null.")
    }

    new DataStream(javaStream.transform("map", implicitly[TypeInformation[R]], new MapInvokable[T, R](mapper)))
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](flatMapper: FlatMapFunction[T, R]): DataStream[R] = {
    if (flatMapper == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    new DataStream[R](javaStream.transform("flatMap", implicitly[TypeInformation[R]], new FlatMapInvokable[T, R](flatMapper)))
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](fun: (T, Collector[R]) => Unit): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val flatMapper = new FlatMapFunction[T, R] {
      val cleanFun = clean(fun)
      def flatMap(in: T, out: Collector[R]) { cleanFun(in, out) }
    }
    flatMap(flatMapper)
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](fun: T => TraversableOnce[R]): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val flatMapper = new FlatMapFunction[T, R] {
      val cleanFun = clean(fun)
      def flatMap(in: T, out: Collector[R]) { cleanFun(in) foreach out.collect }
    }
    flatMap(flatMapper)
  }

  /**
   * Creates a new [[DataStream]] by reducing the elements of this DataStream using an associative reduce
   * function.
   */
  def reduce(reducer: ReduceFunction[T]): DataStream[T] = {
    if (reducer == null) {
      throw new NullPointerException("Reduce function must not be null.")
    }
    javaStream match {
      case ds: GroupedDataStream[_] => new DataStream[T](javaStream.transform("reduce", javaStream.getType(), new GroupedReduceInvokable[T](reducer, ds.getKeySelector())))
      case _ => new DataStream[T](javaStream.transform("reduce", javaStream.getType(), new StreamReduceInvokable[T](reducer)))
    }
  }

  /**
   * Creates a new [[DataStream]] by reducing the elements of this DataStream using an associative reduce
   * function.
   */
  def reduce(fun: (T, T) => T): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("Reduce function must not be null.")
    }
    val reducer = new ReduceFunction[T] {
      val cleanFun = clean(fun)
      def reduce(v1: T, v2: T) = { cleanFun(v1, v2) }
    }
    reduce(reducer)
  }

  /**
   * Creates a new DataStream that contains only the elements satisfying the given filter predicate.
   */
  def filter(filter: FilterFunction[T]): DataStream[T] = {
    if (filter == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    new DataStream[T](javaStream.filter(filter))
  }

  /**
   * Creates a new DataStream that contains only the elements satisfying the given filter predicate.
   */
  def filter(fun: T => Boolean): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    val filter = new FilterFunction[T] {
      val cleanFun = clean(fun)
      def filter(in: T) = cleanFun(in)
    }
    this.filter(filter)
  }

  /**
   * Writes a DataStream to the standard output stream (stdout). For each
   * element of the DataStream the result of .toString is
   * written.
   *
   */
  def print(): DataStream[T] = new DataStream[T](javaStream.print())

  /**
   * Writes a DataStream to the file specified by path in text format. The
   * writing is performed periodically, in every millis milliseconds. For
   * every element of the DataStream the result of .toString
   * is written.
   *
   */
  def writeAsText(path: String, millis: Long): DataStream[T] = new DataStream[T](javaStream.writeAsText(path, millis))

  /**
   * Writes a DataStream to the file specified by path in text format.
   * For every element of the DataStream the result of .toString
   * is written.
   *
   */
  def writeAsText(path: String): DataStream[T] = new DataStream[T](javaStream.writeAsText(path))

  /**
   * Writes a DataStream to the file specified by path in text format. The
   * writing is performed periodically, in every millis milliseconds. For
   * every element of the DataStream the result of .toString
   * is written.
   *
   */
  def writeAsCsv(path: String, millis: Long): DataStream[T] = new DataStream[T](javaStream.writeAsCsv(path, millis))

  /**
   * Writes a DataStream to the file specified by path in text format.
   * For every element of the DataStream the result of .toString
   * is written.
   *
   */
  def writeAsCsv(path: String): DataStream[T] = new DataStream[T](javaStream.writeAsCsv(path))

  /**
   * Adds the given sink to this DataStream. Only streams with sinks added
   * will be executed once the StreamExecutionEnvironment.execute(...)
   * method is called.
   *
   */
  def addSink(sinkFuntion: SinkFunction[T]): DataStream[T] = new DataStream[T](javaStream.addSink(sinkFuntion))

  /**
   * Adds the given sink to this DataStream. Only streams with sinks added
   * will be executed once the StreamExecutionEnvironment.execute(...)
   * method is called.
   *
   */
  def addSink(fun: T => Unit): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("Sink function must not be null.")
    }
    val sinkFunction = new SinkFunction[T] {
      val cleanFun = clean(fun)
      def invoke(in: T) = cleanFun(in)
    }
    this.addSink(sinkFunction)
  }

}