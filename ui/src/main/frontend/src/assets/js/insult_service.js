/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module insult-js/insult_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JInsultService = Java.type('com.redhat.labs.insult.services.InsultService');

/**
 @class
*/
var InsultService = function(j_val) {

  var j_insultService = j_val;
  var that = this;

  /**

   @public
   @param resultHandler {function} 
   */
  this.getInsult = function(resultHandler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_insultService["getInsult(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param name {string} 
   @param resultHandler {function} 
   */
  this.namedInsult = function(name, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_insultService["namedInsult(java.lang.String,io.vertx.core.Handler)"](name, function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param resultHandler {function} 
   */
  this.check = function(resultHandler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_insultService["check(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_insultService;
};

InsultService._jclass = utils.getJavaClass("com.redhat.labs.insult.services.InsultService");
InsultService._jtype = {
  accept: function(obj) {
    return InsultService._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(InsultService.prototype, {});
    InsultService.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
InsultService._create = function(jdel) {
  var obj = Object.create(InsultService.prototype, {});
  InsultService.apply(obj, arguments);
  return obj;
}
/**

 @memberof module:insult-js/insult_service
 @param vertx {Vertx} 
 @return {InsultService}
 */
InsultService.create = function(vertx) {
  var __args = arguments;
  if (__args.length === 1 && typeof __args[0] === 'object' && __args[0]._jdel) {
    return utils.convReturnVertxGen(InsultService, JInsultService["create(io.vertx.core.Vertx)"](vertx._jdel));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**

 @memberof module:insult-js/insult_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {InsultService}
 */
InsultService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(InsultService, JInsultService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address));
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = InsultService;