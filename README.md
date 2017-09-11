# amazon-bill-guesser

Answering the age-old question "did I actually buy that?" w.r.t. Amazon
credit card charges.

## Problem

Amazon's charges on my credit card do not always match my order
totals, nor do they match my individual line items. Instead, the
credit card charges show up based on some
[partition](https://en.wikipedia.org/wiki/Partition_of_a_set) of the
order items, where each charge is the sum of one of the partition
members.  So for an order with three items costing 1,2, and 3, there
are five possible ways charges might show up:

    (->> (combo/partitions [1 2 3])
     (map (fn [part]
            (map #(apply + %) part))))

    => ((6) (3 3) (4 2) (1 5) (1 2 3))

If you have hundreds of orders to reconcile, this quickly becomes tedious.

## Approach

The amazon-bill-guesser namespace provides functions that can slurp in
Amazon's "Orders and Shipments" CSV file, and then create a map from
every possible charge that might appear on your credit card to one or
more plausible explanations of where that charge came from. You can
type in a charge amount at the REPL, and see the explanations.

## Usage

First [install Clojure](https://clojure.org/guides/clj) and clone this
repo.

Download a report of type "Orders and shipments" from
https://amazon.com, navigating via:

    Account & Lists -> Your Account -> Download order reports

Start a REPL from the repo directory:

    clojure    

Then start the guesser and follow the prompts:

    (require 'com.stuarthalloway.amazon-bill-guesser :as abg)
    (abg/guesser "your-csv-file-name")

## Other Approaches and Open Questions

It is possible that Amazon provides a report that contains enough
information to answer this question directly, but I couldn't find it,
and other people [report similar problems](http://www.teddideppner.com/2016/08/how-to-reconcile-amazon-com-orders-with-credit-card-charges/).

I did not investigate what happens when an order is partially
fulfilled within the time range of a report, and partially outside
it. I just pick a time range broad enough to include everything I care
about.


