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
   [clojure.pprint :as pp]))

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
   "Payment Instrument Type", nil
   "Website", nil
   "Purchase Order Number", nil
   "Ordering Customer Email", :email
   "Shipment Date", :ship-date,
   "Shipping Address Name", :ship-name
   "Shipping Address Street 1", nil
   "Shipping Address Street 2", nil
   "Shipping Address City", :ship-city
   "Shipping Address State", nil
   "Shipping Address Zip", nil
   "Order Status", nil
   "Carrier Name & Tracking Number", nil
   "Subtotal", nil
   "Shipping Charge", nil
   "Tax Before Promotions", nil
   "Total Promotions", nil
   "Tax Charged", nil
   "Total Charged", [:total csv-usd->bigdec]
   "Buyer Name", nil
   "Group Name", nil})

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
  
(defn load-orders-and-shipments
  "Load an orders-and-shipments report from amazon.com"
  [csv-file]
  (with-open [rdr (io/reader csv-file)]
    (let [[hdr & data] (csv/read-csv rdr)]
      (into
       []
       (comp (map #(zipmap hdr %))
             (map translate))
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

(def line-item-subsets
  "xform from an order into all its nontrivial subsets"
  (comp
   (map seq)
   (map combo/subsets)
   cat
   (filter seq)))

(defn possible-bill-amounts
  "Returns a map from dollar amounts to a set of different explanations
that tally to that dollar amount."
  [orders]
  (transduce
   line-item-subsets
   (completing
    (fn [m line-items]
      (let [k (apply + (map :total line-items))]
        (update m k (fnil conj #{}) line-items))))
   {}
   (vals orders)))

(def table-keys [:total :email :ship-city :order-date :ship-date])

(defn print-guess
  [n guess]
  (println "Option " n ": " (:order-id (first guess)))
  (pp/print-table table-keys guess)
  (println))

(defn print-guesses
  [pbm n]
  (let [amount (bigdec n)]
    (if-let [guesses (get pbm amount)]
      (do
        (println "There are" (count guesses) "possible explanation(s) for $" n ":\n")
        (doseq [[idx guess] (map-indexed vector guesses)]
          (print-guess (inc idx) guess)))
      (println "No amounts matched.\n"))))

(defn guesser
  "Launch a REPL UI that lets you enter dollar amounts, guessing
how the Amazon orders-and-shipments report in csv-file might
account for those dollar amounts."
  [csv-file]
  (let [rows (load-orders-and-shipments csv-file)
        orders (bin-by rows :order-id)
        pbm (possible-bill-amounts orders)]
    (println csv-file "contains" (count orders) "orders , which could show up as" (count pbm) "distinct dollar amounts on a credit card statement.")
    (loop []
      (println "Enter a USD amount in format n.nn to guess, or :exit to exit")
      (let [step (edn/read *in*)]
        (cond
         (number? step) (do (print-guesses pbm step) (recur))
         (= :exit step) :done
         :default (recur))))))
