;   Copyright (c) Stuart Halloway

;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns com.stuarthalloway.amazon-bill-guesser.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::total bigdec?)
(s/def ::line-item (s/keys :req-un [::total]))
(s/def ::line-items (s/coll-of ::line-item))

(defn conform!
  ([spec data] (conform! spec data nil))
  ([spec data context]
     (when-not (s/valid? spec data)
       (throw (ex-info "Data did not match spec"
                       (merge (s/explain-data spec data)
                              (when context {::context context})))))
     data))
