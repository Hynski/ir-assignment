clear all;

%bm25
output();

recall=0:0.1:1;
precision=zeros(5,11);

set1 = data_complex_data_set_display;
set2 = data_data_visualize_display_dataset;
set3 = data_large_data_set_visualization;
set4 = data_visualizing_dataset;

for j=1:4
    
    data1 = set1;
    if j == 2
        data1 = set2;
    elseif j == 3
        data1 = set3;
    elseif j == 4
        data1 = set4;
    end
    
    k = 0;
    for i=1:size(data1,1)/2
        % find precision values 0:0.1:1
        if k > 10
            break
        elseif data1(i,4) >= k/10
            % foreach
            precision(j,1+k) = data1(i,3);
            % avg
            precision(5,1+k) = ((j-1)*precision(5,1+k)+data1(i,3))/j;
            k = k+1;
        end
    end
    
end

figure(1);
hold on;
% average
plot(recall,precision(5,:),'-xb');

plot(recall,precision(1,:),'--or');
plot(recall,precision(2,:),'--dc');
plot(recall,precision(3,:),'--sk');
% one with missing data for recall=1
plot(recall(1:10),precision(4,1:10),'--+m');


axis([0 1 0.55 1]);
grid on;

xlabel('saanti (recall)');
ylabel('tarkkuus (precision)');
legend('BM25 keskiarvo', 'hakulauseke 1', 'hakulauseke 2', 'hakulauseke 3', 'hakulauseke 4');
