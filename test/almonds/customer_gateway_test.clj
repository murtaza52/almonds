(ns almonds.customer-gateway-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.resource :as r]
            [almonds.customer-gateway :as cg]))



(def my-resources  [{:id-tag :g1 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.14.214"}
                    {:id-tag :g2 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.15.215"}
                    {:id-tag :g3 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.16.216"}])

(comment
  (r/reset-state)
  (r/commit :murtaza-sandbox my-resources)
  (r/diff-stack :murtaza-sandbox)
  (r/apply-diff)

  @r/commit-state)
