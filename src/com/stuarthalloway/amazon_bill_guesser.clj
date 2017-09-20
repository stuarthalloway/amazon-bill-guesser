;   Copyright (c) Stuart Halloway

;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns com.stuarthalloway.amazon-bill-guesser
  (:require
   [clojure.data.csv :as csv]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.math.combinatorics :as combo]
   [clojure.pprint :as pp]
   [com.stuarthalloway.amazon-bill-guesser.specs :as specs]))

(defn csv-usd->bigdec
  "Convert a string USD rep into a bigdec, or throw"
  [s]
  (if-let [[_ num] (re-matches #"\$([\d.]+)" s)]
    (read-string (str num "M"))
    (throw (ex-info (str "Did not look like USD: [" s "]")))))

(def translation-table
  "Map from AWS CSV headers to one of:

nil              omit
keyword          renaming
[kw f]           rename and translate"
  {"Order Date", :order-date
   "Order ID", :order-id
   "Ordering Customer Email", :email
   "Shipment Date", :ship-date,
   "Shipping Address Name", :ship-name
   "Shipping Address City", :ship-city
   "Item Total", [:total csv-usd->bigdec]})

(defn translate
  "Apply translation table to a map from strings to values."
  [m]
  (reduce-kv
   (fn [m k v]
     (if-let [trans (get translation-table k)]
       (if (keyword? trans)
         (assoc m trans v)
         (let [[kw f] trans]
           (assoc m kw (f v))))
       m))
   {}
   m))
  
(defn load-items
  "Load an items report from amazon.com"
  [csv-file]
  (with-open [rdr (io/reader csv-file)]
    (let [[hdr & data] (csv/read-csv rdr)]
      (into
       []
       (comp (map #(zipmap hdr %))
             (map (fn [raw] [raw (translate raw)]))
             (map (fn [[raw line-item]]
                    (specs/conform! ::specs/line-item line-item raw))))
       data))))

(defn bin-by
  "Given a collection of entity maps, return a map from
keyfn of entity to a set of entities matching that keyfn."
  [entities keyfn]
  (reduce
   (fn [m ent]
     (update m (keyfn ent) (fnil conj #{}) ent))
   {}
   entities))

(defn progress
  [ch n]
  (let [a (atom 0)]
    (map (fn [x]
           (when (zero? (mod (swap! a inc) n))
             (print ch)
             (flush))
           x))))

(defn possible-shipments
  [amount orders-map limit]
  (eduction
   (comp
    (map seq)
    (map combo/subsets)
    cat
    (take limit)
    (filter #(= amount (apply + (map :total %))))
    (map-indexed vector))
   (sort-by count (vals orders-map))))

(def table-keys [:total :email :ship-city :order-date :ship-date])

(defn print-guess
  [n guess]
  (println "Option" (inc n) ":" (:order-id (first guess)) (:order-date (first guess)))
  (pp/print-table table-keys guess)
  (println))

(defn print-guesses
  [orders-map input consider-limit guess-limit]
  (let [amount (bigdec input)
        ct (reduce
            (completing (fn [ct [n guess]]
                          (print-guess n guess)
                          (let [nct (inc ct)]
                            (if (<= guess-limit nct)
                              (reduced nct)
                              nct))))
            0
            (possible-shipments amount orders-map consider-limit))]
    (println "Showing" ct "possible explanation(s) for $" amount)))

(defn guesser
  "Launch a REPL UI that lets you enter dollar amounts, guessing
how the Amazon items report in csv-file might
account for those dollar amounts."
  [csv-file]
  (let [rows (load-items csv-file)
        _ (println "Item Count: " (count rows))
        order-map (bin-by rows :order-id)
        _ (println "Order Count: " (count order-map))]
    (loop []
      (println "Enter a USD amount in format n.nn to guess, or :exit to exit")
      (let [step (edn/read *in*)]
        (cond
         (number? step) (do (print-guesses order-map step 5000000 10) (recur))
         (= :exit step) :done
         :default (recur))))))
