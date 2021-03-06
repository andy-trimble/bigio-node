/*
 * Copyright (c) 2015, Archarithms Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 */

var logger = require('winston');
var db = require('./member/member-database');
var utils = require('./utils');

var gossipInterval, cleanupInterval;

var me;

module.exports = {

    /**
     * Start the gossiping task.
     * @param {Object} me the local member.
     * @param {Object} config the configuration object.
     */
    initialize: function(me, config) {
        this.me = me;

        gossipInterval = config.gossipInterval;
        cleanupInterval = config.cleanupInterval;

        // start the periodic task
        setInterval(function() {
            var member, chosenMember;

            var activeKeys = Object.keys(db.activeMembers);
            var activeMemberNum = activeKeys.length;

            if (activeMemberNum > 1) {
                var tries = 10;
                do {
                    var randomNeighborIndex = Math.floor(Math.random() * activeMemberNum);
                    var chosenKey = activeKeys[randomNeighborIndex];
                    chosenMember = db.activeMembers[chosenKey];

                    if (--tries <= 0) {
                        chosenMember = undefined;
                        break;
                    }
                } while (me.equals(chosenMember));
            }

            if(!me.equals(chosenMember)) {
                member = chosenMember;
            }

            if (member !== undefined) {
                var memberList = {};
                memberList.ip = me.ip;
                memberList.gossipPort = me.gossipPort;
                memberList.dataPort = me.dataPort;
                memberList.millisecondsSinceMidnight = utils.getMillisecondsSinceMidnight();
                memberList.publicKey = me.publicKey;
                memberList.tags = me.tags;
                memberList.members = [];
                memberList.eventListeners = {};
                memberList.clock = [];

                for(var i = 0; i < activeMemberNum; ++i) {
                    var k = activeKeys[i];
                    var m = db.activeMembers[k];
                    memberList.members.push(m.ip + ":" + m.gossipPort + ":" + m.dataPort);

                    if(m === me) {
                        m.sequence += 1;
                    }
                    memberList.clock[i] = m.sequence;
                }

                var regs = db.getAllRegistrations();
                for(var indx in regs) {
                    var key = regs[indx].member.ip + ":" + regs[indx].member.gossipPort + ":" + regs[indx].member.dataPort;
                    if(memberList.eventListeners[key] === undefined) {
                        memberList.eventListeners[key] = [];
                    }
                    memberList.eventListeners[key].push(regs[indx].topic);
                }

                member.gossip(memberList);
            }
        }, gossipInterval);
    },

    /**
     * Shutdown the gossiping task.
     * @param {function} the callback.
     */
    shutdown: function(cb) {
        // TODO: Gracefully shut down the gossip task
        cb();
    }
};
