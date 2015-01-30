(ns almonds.contract
  (:require [slingshot.slingshot :refer [throw+]]))

(defmacro defmulti-with-default [name]
  `(do
     (defmulti ~name :almonds-type)
     (defmethod ~name :default [args#]
       (throw+ {:type ::bad-almonds-type
                :operation '~name
                :args args#}))))

(defmulti-with-default create)
(defmulti-with-default sanitize)
(defmulti-with-default retrieve-all)
(defmulti-with-default validate)
(defmulti-with-default delete)
(defmulti-with-default aws-id-key)
(defmulti-with-default pre-staging)
(defmulti-with-default is-dependent?)
(defmulti-with-default dependent-types)
(defmulti-with-default parent-type)

(defmulti prepare-almonds-tags :almonds-type)

(defmulti get-default-dependents :almonds-type)
(defmethod get-default-dependents :default [_] [])

(defmulti dependents :almonds-type)
(defmethod dependents :default [_] [])
