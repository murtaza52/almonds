;;; Pallet project configuration file

(require
 '[almonds.groups.almonds :refer [almonds]])

(defproject almonds
  :provider {:jclouds
             {:node-spec
              {:image {:os-family :ubuntu :os-version-matches "12.04"
                       :os-64-bit true}}}}

  :groups [almonds])
