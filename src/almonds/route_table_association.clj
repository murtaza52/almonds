;; (ns almonds.route-table-association
;;   (:require [almonds.resource :as r]))

;; (defrecord RouteTableAssociation [almonds-id subnet route-table]
;;   (tf [this]
;;     (r/to-json {"resource"
;;                 {"aws_route_table_association"
;;                  {almonds-id
;;                   {"subnet_id" (r/tf-id subnet)
;;                    "route_table_id" (r/tf-id route-table)}}}})))
