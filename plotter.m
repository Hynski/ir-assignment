function [ ] = Plotter( )
%PLOTTER Plots the data

% load the output/results from 'output.m'
clear all;
output();

% init
recall=0:0.1:1;

[pre1, pre2] = avgprecision(data_complex_data_set_display, data_data_visualize_display_dataset, data_large_data_set_visualization, data_visualizing_dataset);
[pre1stem, pre2stem] = avgprecision(stemdata_complex_data_set_display, stemdata_data_visualize_display_dataset, stemdata_large_data_set_visualization, stemdata_visualizing_dataset);

figure(1);
hold on;
plot(recall, pre1, '-xb');
plot(recall, pre2, '--or');
plot(recall, pre1stem, ':+c');
plot(recall, pre2stem, '-.dm');

xlabel('saanti (recall)');
ylabel('tarkkuus (precision)');
legend('BM25','VSM TF-IDF','BM25 & Porter-stemmaus','VSM TF-IDF & Porter-stemmaus');

grid on;
%axis([0 1 0.5 1]);

end

