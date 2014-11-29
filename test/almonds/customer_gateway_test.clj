(ns almonds.customer-gateway-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.resource :as r]
            [almonds.customer-gateway :as cg]))



(def my-resources  [{:almonds-tags :g1 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.14.214"}
                    {:almonds-tags :g2 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.15.215"}
                    {:almonds-tags :g3 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.16.216"}])

(comment
  (r/reset-state)
  (r/stage :murtaza-sandbox my-resources)
  (r/diff :murtaza-sandbox)
  (r/push)

  @r/stage-state)
