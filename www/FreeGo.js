var exec = require('cordova/exec');

exports.echo = function (arg0, success, error) {
    exec(success, error, 'Pos', 'echo', [arg0]);
};
exports.beep = function (arg0, success, error) {
    exec(success, error, 'Pos', 'beep', [arg0]);
};
exports.print = function (tn, pri, ins, dt, qr, tid, success, error) {
    exec(success, error, 'Pos', 'print', [tn, pri, ins, dt, qr, tid]);
};
exports.scan = function (arg0, success, error) {
    if(!arg0) arg0 = 5000;
    exec(success, error, 'Pos', 'scan', [arg0]);
};
exports.read_id = function (arg0, success, error) {
    if(!arg0) arg0 = 5;
    exec(success, error, 'Pos', 'read_id', [arg0]);
};
exports.scan_by_camera = function (success, error) {
    exec(success, error, 'Pos', 'scan_by_camera', []);
};
exports.led = function (arg0, arg1, success, error) {
    exec(success, error, 'Pos', 'led', [arg0, arg1]);
};
exports.exit = function () {
    exec(function(){}, function(){}, 'Pos', 'exit', []);
};