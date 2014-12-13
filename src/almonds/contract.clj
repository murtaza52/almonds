(ns almonds.contract
  (:require [slingshot.slingshot :refer [throw+]]
            [clojure.data.json :refer [write-str]]))

(defmacro defmulti-with-default [name]
  `(do
     (defmulti ~name :almonds-type)
     (defmethod ~name :default [args#]
       (throw+ {:operation '~name
                :args (print-str args#)
                :msg "Either :type was not given or is an incorrect value."}))))

(defmulti-with-default create)
(defmulti-with-default sanitize)
(defmulti-with-default retrieve-all)
(defmulti-with-default validate)
(defmulti-with-default delete)
(defmulti-with-default aws-id-key)
(defmulti-with-default dependents)
(defmulti-with-default pre-staging)
