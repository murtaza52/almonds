;; (ns almonds.route-table-association
;;   (:require [almonds.resource :as r]))

;; (defrecord RouteTableAssociation [almonds-tags subnet route-table]
;;   (tf [this]
;;     (r/to-json {"resource"
;;                 {"aws_route_table_association"
;;                  {almonds-tags
;;                   {"subnet_id" (r/tf-id subnet)
;;                    "route_table_id" (r/tf-id route-table)}}}})))
