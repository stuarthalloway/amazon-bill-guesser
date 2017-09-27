# amazon-bill-guesser

Answering the age-old question "did I actually buy that?" w.r.t. Amazon
credit card charges.

## Problem

Amazon's charges on my credit card do not always match my order
totals, nor do they match my individual line items. Instead, the
credit card charges may show up based on some
[partition](https://en.wikipedia.org/wiki/Partition_of_a_set) of the
order items, where each charge is the sum of one of the partition
members.  So for an order with three items costing 1,2, and 3, there
are five possible ways charges might show up:

    (->> (combo/partitions [1 2 3])
     (map (fn [part]
            (map #(apply + %) part))))

    => ((6) (3 3) (4 2) (1 5) (1 2 3))

If you happen to have many order splits, this becomes tedious...

## Approach

... and by "tedious" I mean "intractable". Finding order line items
that match a credit card charge is actually the [subset sum
problem](https://en.wikipedia.org/wiki/Subset_sum_problem), which is
actually _actually_ the [knapsack
problem](https://en.wikipedia.org/wiki/Knapsack_problem).

The complexity of the algorithms I explored depends on _N_, the number
of decision variables, and/or on _P_, the precision needed to cover
the actual numbers. For the Amazon bill, _N_ is the max number of
items in a single order, and _P_ is the number of bits needed to
represent the total of the most expensive order.

In my own usage _N_ is pretty small, so this library performs an
exhaustive exponential time search. If you have larger _N_, you will
need to try something else.

## Usage

First [install Clojure](https://clojure.org/guides/deps_and_cli) and clone this
repo.

Download a report of type "Items" from https://amazon.com, navigating via:

    Account & Lists -> Your Account -> Download order reports

Start a REPL from the repo directory:

    clojure    

Then start the guesser and follow the prompts:

    (require '[com.stuarthalloway.amazon-bill-guesser :as abg])
    (abg/guesser "your-csv-file-name")

## Caveats

I did not investigate what happens when an order is partially
fulfilled within the time range of a report, and partially outside
it. I always pick a time range broad enough to include everything I care
about.

## Better Approach

It is possible that Amazon provides a report that contains enough
information to answer this question directly, but I couldn't find it,
and other people [report similar
problems](http://www.teddideppner.com/2016/08/how-to-reconcile-amazon-com-orders-with-credit-card-charges/).
