(ns ^:figwheel-hooks square-draw.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defn root-ui []
  [:div
    [:h1 "Hello World"]])

(rd/render [root-ui] (js/document.querySelector "#app"))