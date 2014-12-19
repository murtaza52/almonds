(ns almonds.state)

(def staging-state (atom {}))
(def pushed-state (atom {}))
(def remote-state (atom {}))
(def resource-types (atom []))

(def first-pull-taken? (atom false))
