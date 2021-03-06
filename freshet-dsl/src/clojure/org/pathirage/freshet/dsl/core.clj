(ns org.pathirage.freshet.dsl.core
  (:refer-clojure :exclude [range])
  (:require [clojure.walk :as walk]
            [clojure.set :as set])
  (:gen-class))

(comment "Most of the DSL constructs are inspired by SQLKorma(http://sqlkorma.com) library by Chris Ganger.")

(defn create-stream
  "Create a stream representing a topic in Kafka."
  [name]
  {:stream name
   :name   name
   :ns "freshet"
   :pk     :id
   :fields []
   :ts     :timestamp})

(defn stream-fields
  "Fields in a stream. These will get retrieved by default in select query if there aren't any projections."
  [stream fields]
  (assoc stream :fields (apply array-map fields)))

(defn pk
  [stream k]
  (assoc stream :pk (keyword k)))

(defn ts
  [stream s]
  (assoc stream :ts (keyword s)))

(defn namespace
  [stream ns]
  (assoc stream :ns ns))

(defmacro defstream
  "Define a stream representing a topic in Kafka, applying functions in the body which changes the stream definition."
  [stream & body]
  `(let [s# (-> (create-stream ~(name stream)) ~@body)]
     (def ~stream s#)))

(defn select*
  "Creates the base query configuration for the given stream."
  [r2s-with-fields]
  (let [fields (cond
                 (map? r2s-with-fields) (:fields r2s-with-fields)
                 (vector? r2s-with-fields) r2s-with-fields
                 :else (throw (Exception. (str "Unsupported fields spec: " r2s-with-fields))))
        r2s (if (map? r2s-with-fields)
              (:r2s-operator r2s-with-fields)
              :istream)]
    {:type      :select
     :fields    fields
     :r2s       r2s
     :from      []
     :modifiers []
     :window    {:type :window :window-type :unbounded}
     :where     []
     :having    []
     :aliases   #{}
     :group     []
     :aggregate []
     :joins     []}))

(defn- update-fields
  [query fields]
  (let [[first-in-current] (:fields query)]
    (if (= first-in-current :*)
      (assoc query :fields fields)
      (update-in query [:fields] (fn [v1 v2] (vec (concat v1 v2))) fields))))

(defn fields
  "Set fields which should be selected by the query. Fields can be a keyword
  or pair of keywords in a vector [field alias]

  ex: (fields [:name :username] :address :age)"
  [query & fields]
  (let [aliases (set (map second (filter vector? fields)))]
    (-> query
        (update-in [:aliases] set/union aliases)
        (update-fields fields))))

;; TODO: use named parameters for configuring sliding windows.
(defn window-range
  [window seconds]
  (let [window (assoc window :window-type :range)]
    (assoc window :range seconds)))

(defn window-rows
  [window count]
  (let [window (assoc window :window-type :rows)]
    (assoc window :rows count)))

(defn window-now
  [window]
  (assoc window :window-type :now))

(defn window-unbounded
  [window]
  (assoc window :window-type :unbounded))

(defn window*_
  []
  {:type :window})

;; TODO: How to handle multiple stream situation. We need to change how from clause is specified in DSL.
(defmacro window_
  "Set windowing method for stream-to-relational mapping.
  ex: (window (range 30))"
  [query & wm]
  `(let [window# (-> (window*) ~@wm)]
     (update-in ~query [:window] merge window#)))

(defn window*
  [stream]
  {:stream stream})

(defmacro window
  [stream & wspec]
  `(-> (window* ~stream) ~@wspec))

(defn modifiers
  "Set modifier to the select query to filter which results are returned.

  ex: (select wikipedia-stream
        (modifier :distinct)
        (window (range 60)))"
  [query & m]
  (update-in query [:modifiers] conj m))

(comment
  "How where clauses should be transformed"

  (or (> :delta 100) (= :newPage "True"))

  {::pred or ::args [{::pred > ::args [:delta 100]} {::pred = ::args [:newPage "True"]}]}

  "Binding based approach used in Korma is needed to implement aliases and table prefixes.")

;; TODO: Difference between where and having is important. Where is executed before perfoming any aggregations --
;; TODO: basically to filter rows in a relation before performing group by and aggregations -- having is executed after
;; TODO: aggregations are done.

(def predicates
  {'and  'org.pathirage.freshet.dsl.core/pred-and
   'or   'org.pathirage.freshet.dsl.core/pred-or
   '=    'org.pathirage.freshet.dsl.core/pred-=
   'not= 'org.pathirage.freshet.dsl.core/pred-not=
   '<    'org.pathirage.freshet.dsl.core/pred-<
   '>    'org.pathirage.freshet.dsl.core/pred->
   '<=   'org.pathirage.freshet.dsl.core/pred-<=
   '>=   'org.pathirage.freshet.dsl.core/pred->=
   'not  'org.pathirage.freshet.dsl.core/pred-not})

(defn pred-and
  [l r]
  {:pred :and :args [l r]})

(defn pred-or
  [l r]
  {:pred :or :args [l r]})

(defn pred-=
  [l r]
  {:pred := :args [l r]})

(defn pred-not=
  [l r]
  {:pred :not= :args [l r]})

(defn pred-<
  [l r]
  {:pred :< :args [l r]})

(defn pred->
  [l r]
  {:pred :> :args [l r]})

(defn pred-<=
  [l r]
  {:pred :<= :args [l r]})

(defn pred->=
  [l r]
  {:pred :>= :args [l r]})

(defn pred-not
  [l r]
  {:pred :not :args [l r]})

(defn pred-conj
  [l r]
  (if (empty? l)
    r
    {:pred :and :args (conj l r)}))

(defn parse-where
  [form]
  (walk/postwalk-replace predicates form))

(defn- handle-where-or-having-clauses
  [where*or-having* query form]
  `(let [q# ~query]
     (~where*or-having* q# ~(parse-where `~form))))

(defn where*
  "Add where clauses to the query. Clauses are a map and will be joined together via AND to the existing clauses."
  [query clause]
  (update-in query [:where] pred-conj clause))

(defmacro where
  "Add where clauses to query, clauses can express in clojure with keywords to refer to the stream fields.

  ex: (where query (> :delta 100))


  Supported predicates: and, or, =, not=, <, >, <=, >=, not"
  [query form]
  (handle-where-or-having-clauses #'where* query form))

(defn having*
  "Add having clauses to the query. Clauses are a map and will be joined together via AND to the existing clauses."
  [query clause]
  (update-in query [:having] pred-conj clause))

(defmacro having
  "Add where clauses to query, clauses can express in clojure with keywords to refer to the stream fields.

  ex: (where query (> :delta 100))


  Supported predicates: and, or, =, not=, <, >, <=, >=, not"
  [query form]
  (handle-where-or-having-clauses #'having* query form))

(defn istream
  [fields-with-renaming]
  {:r2s-operator :istream :fields fields-with-renaming})

(defn rstream
  [fields-with-renaming]
  {:r2s-operator :rstream :fields fields-with-renaming})

(defn from*
  [query f]
  (let [normalized-f (vec (map #(if (contains? % :window-type) % {:window-type :unbounded :stream %}) f))]
    (update-in query [:from] into normalized-f)))

(defmacro from
  [query s2r]
  `(from* ~query ~s2r))

(defn execute-query
  "Execute a continuous query. Query will first get converted to extension of relation algebra, then
  to physical query plan before getting deployed in to the stream processing engine."
  [query]
  (prn query))

(defmacro select
  "Build a select query, apply any modifiers specified in the body and then generate and submit DAG of Samza jobs
  which is the physical execution plan of the continuous query on stream specified by `stream`. `stream` is an stream
  created by `defstream`. Returns a job identifier which can used to monitor the query or error incase of a failure.

  ex: (select stock-ticks
        (fields :symbol :bid :ask)
        (where {:symbol 'APPL'}))"
  [fwm & body]
  `(let [query# (-> (select* ~fwm) ~@body)]
     query#))


